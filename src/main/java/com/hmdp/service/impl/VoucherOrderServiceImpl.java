package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.MQConstant;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_KEY;
import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;

    private final TransactionTemplate transactionTemplate;

    private final RedisIDWorker redisIDWorker;

    private final StringRedisTemplate stringRedisTemplate;

    private final RabbitTemplate rabbitTemplate;

    private final RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("static/SECOND_KILL_VOUCHER.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1.检查优惠卷
        // 1.1 是否存在优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null || ObjectUtils.isEmpty(voucher)) {
            return Result.fail("秒杀券不存在");
        }
        // 2.检查秒杀券日期状态
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀券尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀券已过期");
        }

        /**
         * 秒杀券异步订单处理 提高更高的并发处理
         * - 新增优惠卷时或者秒杀券同时添加到缓存redis中， 用户lua脚本判断是否有资格下单
         * - 将下单资格和写数据库保存订单分开, 基于lua脚本保证原子性的检验 库存 一人一单
         * - 基于异步线程或者消息队列实现订单写入数据库
         */
        return orderSeckillVoucherAsync(voucher);
    }

    /**
     * 单节点秒杀券下单购买 一人一单
     * - 本业务中需要注意spring注解事务生效：
     * - 保证方法未非私有
     * - 注解@Transactional针对的是springioc中的代理对象，而不是原始对象
     * - 并发安全情况下要确保先提交事务后再释放锁
     * @param voucher
     * @return
     */
//    @Transactional
    public synchronized Result singleOrderSecondVoucher(SeckillVoucher voucher) {
        Long voucherId = voucher.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        return transactionTemplate.execute(status -> {

            try {

                // 用户是否已经购买
                VoucherOrder existsOrder = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId).one();
                if(existsOrder != null) {
                    return Result.fail("每个用户仅限购买一张");
                }

                // 3.库存是否充足
                if (voucher.getStock() < 1) {
                    return Result.fail("库存不足");
                }
                // 4.扣减库存
                boolean success = seckillVoucherService.lambdaUpdate()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0) // CAS乐观锁 >0
                        .update();
                if(!success) {
                    return Result.fail("库存不足");
                }
                // 5.创建订单
                VoucherOrder order = createOrder(userId, voucherId);
                save(order);
                return Result.ok("购买成功");
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException(e);
            }
        });
    }

    private VoucherOrder createOrder(Long voucherId,Long userId) {
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIDWorker.nextId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        return order;
    }

    /**
     * 集群环境下分布式锁秒杀券下单购买 一人一单
     * @param voucher
     * @return
     */
    public Result clusterOrderSecondVoucher(SeckillVoucher voucher) {
        Long userId = UserHolder.getUser().getId();
        Long voucherId = voucher.getVoucherId();

//        LockUtils lockUtils = new LockUtils("order:" + userId, stringRedisTemplate);
//        boolean isLock = lockUtils.tryLock(10L);
        /**
         * 基于redisson获取分布式锁 并且不阻塞方式获取
         */
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock) {
            /**
             * 锁的粒度已经控制在用户身上 获取不到锁可能是重复点击 秒杀卷能不能抢到要看库存之类情况
             */
            return Result.fail("请不要重复点击");
        }

        return transactionTemplate.execute(status -> {

            try {
                // 用户是否已经购买
                VoucherOrder existsOrder = lambdaQuery().eq(VoucherOrder::getUserId, userId)
                        .eq(VoucherOrder::getVoucherId, voucherId).one();
                if (existsOrder != null) {
                    return Result.fail("每个用户仅限购买一张");
                }

                // 3.库存是否充足
                if (voucher.getStock() < 1) {
                    return Result.fail("库存不足");
                }
                // 4.扣减库存
                boolean success = seckillVoucherService.lambdaUpdate()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0) // CAS乐观锁 >0
                        .update();
                if (!success) {
                    return Result.fail("库存不足");
                }
                // 5.创建订单
                VoucherOrder order = createOrder(voucherId, userId);
                save(order);
                return Result.ok("购买成功");
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException(e);
            } finally {
                lock.unlock();
            }
        });
    }

    /**
     * 最终版 异步秒杀券下单购买
     * @param voucher
     * @return
     */
    public Result orderSeckillVoucherAsync(SeckillVoucher voucher) {
        Long userId = UserHolder.getUser().getId();
        Long voucherId = voucher.getVoucherId();
        // 修改参数传递方式：KEYS[1], KEYS[2], ARGV[1]对应用户ID
        // ！！注意：返回值接受错误库存还会扣减 无法回滚！！
        long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                List.of((SECKILL_STOCK_KEY + voucherId), SECKILL_ORDER_KEY),
                userId.toString());
        if(result == 1L) {
            return Result.fail("库存不足");
        }else if(result == 2L) {
            return Result.fail("请不要重复点击");
        }

        VoucherOrder order = createOrder(voucherId, userId);
        rabbitTemplate.convertAndSend(MQConstant.DEFAULT_DIRECT_EXC,MQConstant.ORDER_SECKILL_BIND_KEY,order);
        // 异步下单实现逻辑
        return Result.ok(order.getId());
    }
}

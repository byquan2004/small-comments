package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;

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

    @Override
    public Result seckillVoucher(Long voucherId) {
        return orderSecondVoucher(voucherId);
    }

    /**
     * 秒杀券下单购买
     * - 本业务中需要注意spring注解事务生效：
     * - 保证方法未非私有
     * - 注解@Transactional针对的是springioc中的代理对象，而不是原始对象
     * - 并发安全情况下要确保先提交事务后再释放锁
     * @param voucherId
     * @return
     */
//    @Transactional
    public synchronized Result orderSecondVoucher(Long voucherId) {
        transactionTemplate.execute(status -> {

            try {
                // 1.检查优惠卷
                // 1.1 是否存在优惠卷
                SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
                if(voucher == null || ObjectUtils.isEmpty(voucher)) {
                    return Result.fail("秒杀券不存在");
                }
                // 1.2 用户是否已经购买
                UserDTO user = UserHolder.getUser();
                VoucherOrder existsOrder = lambdaQuery().eq(VoucherOrder::getUserId, user.getId())
                        .eq(VoucherOrder::getVoucherId, voucherId).one();
                if(existsOrder != null) {
                    return Result.fail("每个用户仅限购买一张");
                }
                // 2.检查秒杀券日期状态
                if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
                    return Result.fail("秒杀券尚未开始");
                }
                if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
                    return Result.fail("秒杀券已过期");
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
                VoucherOrder order = new VoucherOrder();
                long orderId = redisIDWorker.nextId("order");
                order.setId(orderId);
                order.setUserId(user.getId());
                order.setVoucherId(voucherId);
                save(order);
                return Result.ok("购买成功");
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new RuntimeException(e);
            }
        });
        return Result.fail("请稍后重试");
    }
}

package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.LockUtils;
import com.hmdp.utils.RedisData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    @Override
    public Result queryShopById(Long id) {
        return getResultWithBreakdownForMutex(id);
    }

    /**
     * 缓存击穿 分布式互斥锁
     * @param id
     * @return
     */
    private Result getResultWithBreakdownForMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        Shop shop = getShopWithCache(key);
        if (shop != null) return Result.ok(shop);

        String shopLock = LOCK_SHOP_KEY + id;
        LockUtils lockUtils = new LockUtils(shopLock, stringRedisTemplate);
        try {
            // 使用随机延迟减少锁竞争
            int retryCount = 0;
            while (retryCount < 3) { // 最多重试3次
                boolean locked = lockUtils.tryLock(3L);
                if (locked) {
                    // 获取锁成功后再次检查缓存
                    shop = getShopWithCache(key);
                    if (shop != null) return Result.ok(shop);

                    // 查询数据库并重建缓存
                    shop = getById(id);
                    Thread.sleep(200); // 模拟数据库查询延迟
                    if (ObjectUtils.isEmpty(shop)) {
                        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                        return Result.fail("商铺不存在！");
                    }
                    // 添加随机过期时间，避免缓存同时失效
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                            CACHE_SHOP_TTL + RandomUtil.randomLong(1, 10), TimeUnit.MINUTES);
                    return Result.ok(shop);
                } else {
                    // 获取锁失败，随机延迟后重试
                    Thread.sleep(RandomUtil.randomLong(50, 150));
                    retryCount++;
                }
            }

            // 如果多次重试仍未成功，返回默认值或提示信息
            return Result.fail("请稍后重试！");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lockUtils.unlock();
        }
    }

    /**
     * 缓存击穿 逻辑过期
     * @description 适合临时的活动业务 先预热 不存在独立线程重建缓存
     * @param id
     * @return
     */
    private Result getResultWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        if(ObjectUtils.isEmpty(cacheJson)) {
            return Result.fail("商品不存在");
        }
        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 逻辑时间在当前时间之后 未过期
        if(expireTime.isAfter(LocalDateTime.now())) {
            return Result.ok(shop);
        }
        String lockShop = LOCK_SHOP_KEY + id;
        LockUtils lockUtils = new LockUtils(lockShop, stringRedisTemplate);
        try {
            boolean isLock = lockUtils.tryLock(5L);
            if(!isLock) {
                // 返回旧数据
                return Result.ok(shop);
            }

            // double check
            String cacheJson2 = stringRedisTemplate.opsForValue().get(key);
            if(ObjectUtils.isEmpty(cacheJson2)) {
                return Result.fail("商品不存在");
            }
            RedisData redisData2 = JSONUtil.toBean(cacheJson2, RedisData.class);
            Shop shop2 = JSONUtil.toBean((JSONObject) redisData2.getData(), Shop.class);
            LocalDateTime expireTime2 = redisData.getExpireTime();
            // 逻辑时间在当前时间之后 未过期
            if(expireTime2.isAfter(LocalDateTime.now())) {
                return Result.ok(shop2);
            }

            // 独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    System.out.println(Thread.currentThread().getName());
                    Shop dbShop = getById(id);

                    Thread.sleep(200);

                    RedisData data = new RedisData();
                    data.setExpireTime(LocalDateTime.now().plusMinutes(CACHE_SHOP_TTL));
                    data.setData(dbShop);
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    lockUtils.unlock();
                }


            });
            // 返回旧数据
            return Result.ok(shop);
        } catch (Exception e){
            e.printStackTrace();
            Result.fail("商品不存在");
        }
        return Result.fail("商品不存在");
    }


    private Shop getShopWithCache(String key) {
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if(ObjectUtils.isEmpty(shopJson)) {
            return null;
        }
        Shop shop = JSONUtil.toBean(shopJson, Shop.class);
        if(ObjectUtils.isEmpty(shop)) {
            return null;
        }
        return shop;
    }

    /**
     * 缓存穿透
     * @param id
     * @return
     */
    private Result getResultWithPenetration(Long id) {
        String key = CACHE_SHOP_KEY + id;
        Shop shop = getShopWithCache(key);
        if (shop != null) return Result.ok(shop);
        shop = getById(id);
        if(ObjectUtils.isEmpty(shop)) {
            stringRedisTemplate.opsForValue().set(key, "{}",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商铺不存在！");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
                CACHE_SHOP_TTL+ RandomUtil.randomLong(1,10), TimeUnit.MINUTES);
        return Result.ok(shop);
    }

    @Transactional
    @Override
    public void updateShop(Shop shop) {
        updateById( shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
    }
}

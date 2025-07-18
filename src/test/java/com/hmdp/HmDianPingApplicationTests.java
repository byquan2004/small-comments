package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void test() {
        String key = "cache:shop:4";
        Shop dbShop = shopService.getById(4);
        RedisData data = new RedisData();
        data.setExpireTime(LocalDateTime.now().plusMinutes(CACHE_SHOP_TTL));
        data.setData(dbShop);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(data));
    }
}

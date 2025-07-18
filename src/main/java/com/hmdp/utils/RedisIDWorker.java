package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RedisIDWorker {
    /**
     * 时间戳起始点 2022-01-01 00:00:00
     */
    private static final Long BEGIN_TIME = 1640995200L;

    /**
     * 序列号位数
     */
    private static final Long COUNT_BITS = 32L;

    /**
     * RedisTemplate
     */
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成唯一ID
     * @param keyPrefix 前缀
     * @example "order"
     * @return 唯一ID
     */
    public long nextId(String keyPrefix) {
        keyPrefix = "icr:" + keyPrefix;
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIME;
        // 当前日期拼接 key
        String date = now.format(DateTimeFormatter.ofPattern(":yyyy:MM:dd"));
        long nextId = redisTemplate.opsForValue().increment(keyPrefix + date);
        return timestamp << COUNT_BITS | nextId;
    }


}

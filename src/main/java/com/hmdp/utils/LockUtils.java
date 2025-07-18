package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class LockUtils {

    /**
     * 锁名称
     */
    private final String name;

    /**
     * Redis操作模版
     */
    private final StringRedisTemplate stringRedisTemplate;

    public LockUtils(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeout 锁的过期时间 单位秒
     * @return true:获取锁成功，false:获取锁失败
     */
    public boolean tryLock(Long timeout) {
        long threadId = Thread.currentThread().getId();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(this.name, threadId + "", timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放锁
     */
    public void unlock() {
        stringRedisTemplate.delete(this.name);
    }
}

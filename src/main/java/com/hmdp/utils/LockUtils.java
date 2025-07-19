package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class LockUtils {

    /**
     * Redis操作模版
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 锁名称
     */
    private final String name;

    /**
     * 锁前缀
     */
    private static final String keyPrefix = "lock:";

    /**
     * 锁后缀
     */
    private static final String valSuffix = UUID.randomUUID().toString(false);

    /**
     * 释放锁的脚本
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("static/UNLOCK.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public LockUtils(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = keyPrefix + name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间 单位秒
     * @return true:获取锁成功，false:获取锁失败
     */
    public boolean tryLock(Long timeoutSec) {
        /**
         * 并发安全场景还原1：
         * 1.用户多次点击且来之集群节点的请求
         * 2.业务执行耗时过长
         * 3.删除其他线程的锁
         * 所以在加锁时value确保唯一 并且释放判断value 属于当前线程
         */
        String value = Thread.currentThread().getId() + valSuffix;
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(this.name, value, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 脚本释放锁
     */
    public void unlock() {
        String value = Thread.currentThread().getId() + valSuffix;
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(this.name), value);
    }

    /**
     * 释放锁
     */
    public void codeUnlock() {
        String value = Thread.currentThread().getId() + valSuffix;
        String cacheVal = stringRedisTemplate.opsForValue().get(this.name);

        if(value.equals(cacheVal)) {
            /**
             * 并发安全场景还原2:
             * 1.用户多次点击且来之集群节点的请求
             * 2.成功进入当前判断
             * 3.释放锁前阻塞如 jvm full GC
             * 4.锁被自动释放
             * 5.其他线程趁虚而入击成功获取锁
             * 6.阻塞恢复 又把其他线程的锁释放
             * 所以确保判断和释放锁的原子性采用lua脚本或直接使用redisson
             */
            stringRedisTemplate.delete(this.name);
        }

    }
}

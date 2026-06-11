package com.microcourse.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 *
 * 用途（Phase 1）：
 * - 登录失败计数：incr("login:lock:" + username, 30min)
 * - JWT Token 黑名单：set("jwt:blacklist:" + jti, ttl)
 *
 * 注意：key 前缀与 TTL 必须严格按业务逻辑文档 §2.1 / §2.4
 */
@Component
public class RedisUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ------------------- 字符串操作 -------------------

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    // ------------------- 计数操作（登录失败计数） -------------------

    /**
     * 原子递增 + 设置过期（仅首次递增时设过期）
     * 用于：登录失败计数 login:lock:{username}
     */
    public Long incrementWithExpire(String key, long expireSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

    /**
     * 业务快捷方法：登录失败计数（5 次锁 30 分钟）
     * 依据：业务逻辑 §2.1
     */
    public Long incrLoginFailure(String username) {
        return incrementWithExpire("login:lock:" + username, 30 * 60);
    }

    public Integer getLoginFailureCount(String username) {
        Object val = get("login:lock:" + username);
        return val == null ? 0 : Integer.parseInt(val.toString());
    }

    public void clearLoginFailure(String username) {
        delete("login:lock:" + username);
    }

    // ------------------- 黑名单操作（Token 失效） -------------------

    /**
     * 业务快捷方法：JWT Token 加入黑名单
     * 依据：业务逻辑 §2.4
     */
    public void blacklistToken(String jti, long ttlSeconds) {
        set("jwt:blacklist:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public Boolean isTokenBlacklisted(String jti) {
        return hasKey("jwt:blacklist:" + jti);
    }
}

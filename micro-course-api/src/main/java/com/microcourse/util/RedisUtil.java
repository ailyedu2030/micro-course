package com.microcourse.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

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

    public Long incrementWithExpire(String key, long expireSeconds) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

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

    public void blacklistToken(String jti, long ttlSeconds) {
        set("jwt:blacklist:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
    }

    public Boolean isTokenBlacklisted(String jti) {
        return hasKey("jwt:blacklist:" + jti);
    }
}
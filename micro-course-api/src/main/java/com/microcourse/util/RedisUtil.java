package com.microcourse.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

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

    /** 原子 INCR+EXPIRE: 使用 Lua 脚本避免 INCR 后 EXPIRE 前崩溃导致 key 永不过期(CON-007) **/
    public Long incrementWithExpire(String key, long expireSeconds) {
        org.springframework.data.redis.core.script.DefaultRedisScript<Long> script =
                new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        script.setScriptText("local c = redis.call('INCR', KEYS[1]) " +
                             "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                             "return c");
        script.setResultType(Long.class);
        return redisTemplate.execute(script, java.util.Collections.singletonList(key), expireSeconds);
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

    /**
     * Redis 健康检查 ping
     * @return "PONG" if Redis is reachable
     */
    public String ping() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG";
        } catch (Exception e) {
            log.warn("[RedisUtil] ping failed", e);
            throw e;
        }
    }
}
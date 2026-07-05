package com.microcourse.util;

import com.microcourse.metrics.RedisMetrics;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

@Component
public class RedisUtil {

    private static final Logger log = LoggerFactory.getLogger(RedisUtil.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMetrics redisMetrics;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate, RedisMetrics redisMetrics) {
        this.redisTemplate = redisTemplate;
        this.redisMetrics = redisMetrics;
    }

    /** Default TTL for keys without explicit expiration: 1 hour */
    private static final long DEFAULT_TTL_SECONDS = 3600L;

    /**
     * 设置 Key-Value，使用默认 1 小时 TTL (3600s) 防止无界 key 增长。
     * 建议优先使用 {@link #set(String, Object, long, TimeUnit)} 明确指定过期时间。
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, DEFAULT_TTL_SECONDS, TimeUnit.SECONDS);
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

    /**
     * 登录失败计数（Redis 故障时降级为 0，不阻塞登录）
     */
    public Long incrLoginFailure(String username) {
        try {
            return incrementWithExpire("mc:login:lock:" + username, 30 * 60);
        } catch (Exception e) {
            redisMetrics.recordLoginFailureError();
            log.warn("[Redis] incrLoginFailure 失败，降级处理: {}", e.getMessage());
            return 1L;
        }
    }

    /**
     * 获取登录失败次数（Redis 故障时返回 0，不阻塞登录）
     */
    public Integer getLoginFailureCount(String username) {
        try {
            Object val = get("mc:login:lock:" + username);
            return val == null ? 0 : Integer.parseInt(val.toString());
        } catch (Exception e) {
            redisMetrics.recordLoginCheckError();
            log.warn("[Redis] getLoginFailureCount 失败，降级为 0: {}", e.getMessage());
            return 0;
        }
    }

    public void clearLoginFailure(String username) {
        try {
            delete("mc:login:lock:" + username);
        } catch (Exception e) {
            redisMetrics.recordLoginClearError();
            log.warn("[Redis] clearLoginFailure 失败: {}", e.getMessage());
        }
    }

    /**
     * Token 黑名单（Redis 故障时静默降级，不阻止请求）
     */
    public void blacklistToken(String jti, long ttlSeconds) {
        try {
            set("mc:jwt:blacklist:" + jti, "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisMetrics.recordTokenBlacklistError();
            log.warn("[Redis] blacklistToken 失败 jti={}: {}", jti, e.getMessage());
        }
    }

    /**
     * P1I-001: 用户级 Token 黑名单 — 批量作废某个用户的所有活跃 Token。
     * 设置 mc:jwt:user-blacklist:<userId> 标记，JwtAuthenticationFilter 会
     * 在每次请求时检查此标记并拒绝已失效的 Token。
     */
    public void blacklistUserTokens(Long userId, long ttlSeconds) {
        try {
            set("mc:jwt:user-blacklist:" + userId, "1", ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisMetrics.recordTokenBlacklistError();
            log.warn("[Redis] blacklistUserTokens 失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /**
     * P1I-001: 检查用户级 Token 黑名单
     */
    public Boolean isUserTokenBlacklisted(Long userId) {
        try {
            return hasKey("mc:jwt:user-blacklist:" + userId);
        } catch (Exception e) {
            redisMetrics.recordTokenCheckError();
            log.warn("[Redis] isUserTokenBlacklisted 失败 userId={}，降级为未黑名单: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Token 黑名单校验（Redis 故障时返回 false，不误杀合法请求）
     */
    public Boolean isTokenBlacklisted(String jti) {
        try {
            return hasKey("mc:jwt:blacklist:" + jti);
        } catch (Exception e) {
            redisMetrics.recordTokenCheckError();
            log.warn("[Redis] isTokenBlacklisted 失败 jti={}，降级为未黑名单: {}", jti, e.getMessage());
            return false;
        }
    }

    /**
     * Redis 健康检查 ping
     * @return "PONG" if Redis is reachable
     */
    public String ping() {
        try {
            // RES-011 修复: 使用 execute(RedisCallback) 由 RedisTemplate 管理连接生命周期，杜绝连接泄漏
            return redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>) connection -> {
                connection.ping();
                return "PONG";
            });
        } catch (Exception e) {
            redisMetrics.recordPingError();
            log.warn("[RedisUtil] ping failed", e);
            throw e;
        }
    }
}
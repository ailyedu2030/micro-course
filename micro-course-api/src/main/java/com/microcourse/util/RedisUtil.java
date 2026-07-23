package com.microcourse.util;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
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

    /** Refresh token 限流 TTL: 1 小时 (P0-L02 修复,与登录失败计数器隔离) */
    private static final long REFRESH_LIMIT_TTL_SECONDS = 3600L;

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

    protected Long incrementCounter(String key) {
        return redisTemplate.opsForValue().increment(key);
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

    // ==================== P0-L02: Refresh 限流(独立 key 前缀,与登录失败隔离) ====================

    /**
     * 递增 refresh 限流计数 — 同一 IP 每小时最多刷新 20 次
     * 使用独立 key 前缀 mc:refresh:limit: 与登录失败计数 mc:login:lock: 隔离(P0-L02 修复)
     */
    public Long incrRefreshCount(String clientIp) {
        try {
            return incrementWithExpire("mc:refresh:limit:" + clientIp, REFRESH_LIMIT_TTL_SECONDS);
        } catch (Exception e) {
            redisMetrics.recordLoginFailureError();
            log.warn("[Redis] incrRefreshCount 失败,降级处理: {}", e.getMessage());
            return 1L;
        }
    }

    /**
     * 获取 refresh 限流次数
     */
    public Integer getRefreshCount(String clientIp) {
        try {
            Object val = get("mc:refresh:limit:" + clientIp);
            return val == null ? 0 : Integer.parseInt(val.toString());
        } catch (Exception e) {
            redisMetrics.recordLoginCheckError();
            log.warn("[Redis] getRefreshCount 失败,降级为 0: {}", e.getMessage());
            return 0;
        }
    }

    // ==================== P0-S04: Token Generation(登录后旧 refreshToken 失效) ====================

    /**
     * 递增用户 token 代数 — 每次登录 +1,旧 refreshToken 的 tokenGen < currentGen 即失效
     */
    public long incrementTokenGeneration(Long userId) {
        try {
            Long val = incrementCounter("mc:user:token-gen:" + userId);
            return val != null ? val : 0L;
        } catch (Exception e) {
            log.error("[Redis] incrementTokenGeneration 失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 安全状态服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 获取当前用户 token 代数
     */
    public Long getTokenGeneration(Long userId) {
        try {
            Object val = get("mc:user:token-gen:" + userId);
            return val == null ? 0L : ((Number) val).longValue();
        } catch (Exception e) {
            log.error("[Redis] getTokenGeneration 失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 安全状态服务暂时不可用，请稍后重试");
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
            log.error("[Redis] blacklistToken 失败 jti={}", jti, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试");
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
            log.error("[Redis] blacklistUserTokens 失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单服务暂时不可用，请稍后重试");
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
            log.error("[Redis] isUserTokenBlacklisted 失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单校验暂时不可用，请稍后重试");
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
            log.error("[Redis] isTokenBlacklisted 失败 jti={}", jti, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单校验暂时不可用，请稍后重试");
        }
    }

    /**
     * 分布式锁 — 原子 SETNX + EXPIRE（非阻塞）
     * @param key       锁 key
     * @param value     锁值（如线程标识，用于释放时校验）
     * @param ttlSeconds 自动过期秒数
     * @return true 表示获取成功，false 表示已被其他线程持有
     */
    public boolean tryLock(String key, String value, long ttlSeconds) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("[RedisUtil] tryLock 失败 key={}: {}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 释放分布式锁（仅当 value 匹配时释放，防止误删其他线程的锁）
     * @param key   锁 key
     * @param value 锁值（应与 tryLock 时一致）
     */
    public void releaseLock(String key, String value) {
        try {
            String script = "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end";
            org.springframework.data.redis.core.script.DefaultRedisScript<Long> delScript =
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class);
            redisTemplate.execute(delScript, java.util.Collections.singletonList(key), value);
        } catch (Exception e) {
            log.warn("[RedisUtil] releaseLock 失败 key={}: {}", key, e.getMessage());
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

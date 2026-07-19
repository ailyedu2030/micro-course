package com.microcourse.plugin.interactive.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 【BUG #29 修复 P1 性能】 audio_token 流式 GET 的 Redis 缓存层.
 *
 * <p>
 * 问题: 流式 GET /api/courses/{cid}/audio/{token} 每次都要:
 *   1. findByToken (1 SQL)
 *   2. selectById(pageId 或 unitId) (1 SQL)
 * = 2 SQL / 请求, 高并发场景下 DB 压力大.
 * </p>
 *
 * <p>
 * 方案: 用 Redis 缓存 audio_token → AudioStreamInfo JSON, TTL 5 min.
 * - 命中: 0 SQL
 * - 未命中: 2 SQL + 写回 cache
 * </p>
 *
 * <p>
 * 数据一致性: 5 min TTL 内如音频被删除, 用户可能拿到 404 (这是可接受的,
 * 不会导致数据错误, 只是稍延迟). 若需强一致, 可加 invalidation:
 *   generateAudio / updateAudio 时 delete key.
 * </p>
 *
 * <p>
 * 7-19 P1-C 兼容: token 32 字符 UUID, 直接当 key 安全.
 * </p>
 *
 * <p>
 * Redis key 命名: mc:audio:stream:{token}
 * Value: AudioStreamInfo JSON (Jackson)
 * TTL: 5 min (300 s)
 * </p>
 */
@Component
public class AudioStreamCache {

    private static final Logger log = LoggerFactory.getLogger(AudioStreamCache.class);
    private static final String KEY_PREFIX = "mc:audio:stream:";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * 取缓存. Redis 不可用时返回 empty (退化到 DB).
     */
    public Optional<AudioStreamInfo> get(String token) {
        if (redisTemplate == null || objectMapper == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + token);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AudioStreamInfo.class));
        } catch (Exception e) {
            log.warn("[Audio-Cache] GET failed (Redis fallback to DB): token.length={} err={}",
                    token.length(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写缓存. 失败不抛 (best-effort).
     */
    public void put(String token, AudioStreamInfo info) {
        if (redisTemplate == null || objectMapper == null || info == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(KEY_PREFIX + token, json, TTL);
        } catch (Exception e) {
            log.warn("[Audio-Cache] PUT failed: token.length={} err={}", token.length(), e.getMessage());
        }
    }

    /**
     * 主动失效 (生成新音频或删除时调用).
     */
    public void invalidate(String token) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + token);
        } catch (Exception e) {
            log.warn("[Audio-Cache] INVALIDATE failed: token.length={} err={}",
                    token.length(), e.getMessage());
        }
    }
}
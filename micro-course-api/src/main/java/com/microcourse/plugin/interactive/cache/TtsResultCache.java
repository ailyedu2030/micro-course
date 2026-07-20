package com.microcourse.plugin.interactive.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;

/**
 * 【W36 TTS 结果缓存】 spec 6.2 缓存策略
 *
 * <p>
 * 设计:
 * - key: mc:tts:result:{text_hash}:{voice}
 * - text_hash: SHA-256(text) 前 16 字符 hex
 * - value: audio_url (CDN 签名 URL)
 * - TTL: 7 天 (604800 s)
 * </p>
 *
 * <p>
 * 命中条件: 同一段文本 + 同一音色, 直接复用 URL (避免重复 TTS 调用, 节省算力).
 * 失效条件: 主动 invalidate (业务侧更新文本/换音色时).
 * </p>
 */
@Component
public class TtsResultCache {

    private static final Logger log = LoggerFactory.getLogger(TtsResultCache.class);
    private static final String KEY_PREFIX = "mc:tts:result:";
    private static final Duration TTL = Duration.ofDays(7);

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    /**
     * 取缓存的 TTS 结果 URL
     */
    public Optional<String> get(String text, String voice) {
        if (redisTemplate == null || text == null || voice == null) {
            return Optional.empty();
        }
        String key = buildKey(text, voice);
        try {
            String url = redisTemplate.opsForValue().get(key);
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            log.debug("[TTS-Cache] HIT text.length={} voice={}", text.length(), voice);
            return Optional.of(url);
        } catch (Exception e) {
            log.warn("[TTS-Cache] GET failed (Redis fallback to TTS API): err={}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入 TTS 结果 URL 缓存 (best-effort, 失败不抛)
     */
    public void put(String text, String voice, String audioUrl) {
        if (redisTemplate == null || text == null || voice == null || audioUrl == null) {
            return;
        }
        String key = buildKey(text, voice);
        try {
            redisTemplate.opsForValue().set(key, audioUrl, TTL);
            log.debug("[TTS-Cache] PUT text.length={} voice={} url.length={}",
                    text.length(), voice, audioUrl.length());
        } catch (Exception e) {
            log.warn("[TTS-Cache] PUT failed: err={}", e.getMessage());
        }
    }

    /**
     * 主动失效 (文本或音色变更时)
     */
    public void invalidate(String text, String voice) {
        if (redisTemplate == null || text == null || voice == null) return;
        try {
            redisTemplate.delete(buildKey(text, voice));
        } catch (Exception e) {
            log.warn("[TTS-Cache] INVALIDATE failed: err={}", e.getMessage());
        }
    }

    private String buildKey(String text, String voice) {
        return KEY_PREFIX + hash(text) + ":" + voice;
    }

    /**
     * SHA-256 文本 → 16 字符 hex (key 短而唯一)
     */
    private String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
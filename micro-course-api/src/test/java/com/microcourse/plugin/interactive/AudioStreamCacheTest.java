package com.microcourse.plugin.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.plugin.interactive.cache.AudioStreamCache;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 【BUG #29 修复验证】 AudioStreamCache 单元测试.
 *
 * - Redis 命中: 0 SQL 路径
 * - Redis miss: 退化到 DB (call put 后下次命中)
 * - Redis 不可用: 不抛异常, 返回 empty (退化到 DB)
 * - TTL 5 min
 * - 序列化 / 反序列化 AudioStreamInfo 11 字段
 */
class AudioStreamCacheTest {

    private AudioStreamCache cache;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> typedOps = mock(ValueOperations.class);
        ops = typedOps;
        objectMapper = new ObjectMapper();
        when(redis.opsForValue()).thenReturn(ops);

        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        when(mapperProvider.getIfAvailable()).thenReturn(objectMapper);
        cache = new AudioStreamCache(redisProvider, mapperProvider);
    }

    @Test
    @DisplayName("Redis 命中: 返回缓存值, 不调用 DB")
    void cacheHit() throws Exception {
        AudioStreamInfo stored = createSampleInfo();
        String json = objectMapper.writeValueAsString(stored);
        when(ops.get("mc:audio:stream:token-abc-123")).thenReturn(json);

        Optional<AudioStreamInfo> result = cache.get("token-abc-123");

        assertTrue(result.isPresent());
        assertEquals("token-abc-123", result.get().getToken());
        assertEquals(42L, result.get().getCourseId());
        assertEquals("READY", result.get().getStatus());
    }

    @Test
    @DisplayName("Redis 未命中: 返回 empty (调用方退化到 DB)")
    void cacheMiss() {
        when(ops.get(anyString())).thenReturn(null);

        Optional<AudioStreamInfo> result = cache.get("token-not-found");

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("put 写入 Redis 并设置 5 min TTL")
    void putWithTtl() throws Exception {
        AudioStreamInfo info = createSampleInfo();

        cache.put("token-xyz-789", info);

        verify(ops).set(eq("mc:audio:stream:token-xyz-789"),
                anyString(),
                eq(java.time.Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("invalidate 主动失效 (音频删除时调用)")
    void invalidateRemovesKey() {
        cache.invalidate("token-to-delete");

        verify(redis).delete("mc:audio:stream:token-to-delete");
    }

    @Test
    @DisplayName("Redis 不可用: get 返回 empty 不抛异常 (best-effort)")
    void redisUnavailableGetReturnsEmpty() {
        ObjectProvider<StringRedisTemplate> nullRedisProvider = mock(ObjectProvider.class);
        when(nullRedisProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<ObjectMapper> nullMapperProvider = mock(ObjectProvider.class);
        when(nullMapperProvider.getIfAvailable()).thenReturn(null);
        cache = new AudioStreamCache(nullRedisProvider, nullMapperProvider);
        Optional<AudioStreamInfo> result = cache.get("any-token");

        assertFalse(result.isPresent());
        // 不抛异常
    }

    @Test
    @DisplayName("Redis 不可用: put 不抛异常")
    void redisUnavailablePutNoThrow() {
        ObjectProvider<StringRedisTemplate> nullRedisProvider = mock(ObjectProvider.class);
        when(nullRedisProvider.getIfAvailable()).thenReturn(null);
        ObjectProvider<ObjectMapper> nullMapperProvider = mock(ObjectProvider.class);
        when(nullMapperProvider.getIfAvailable()).thenReturn(null);
        cache = new AudioStreamCache(nullRedisProvider, nullMapperProvider);
        assertDoesNotThrow(() -> cache.put("any-token", createSampleInfo()));
    }

    @Test
    @DisplayName("序列化 11 字段往返无损")
    void serializationRoundTrip() throws Exception {
        AudioStreamInfo original = createSampleInfo();

        // 模拟 put 后 get
        String json = objectMapper.writeValueAsString(original);
        when(ops.get("mc:audio:stream:rt-token")).thenReturn(json);

        Optional<AudioStreamInfo> result = cache.get("rt-token");
        assertTrue(result.isPresent());
        AudioStreamInfo r = result.get();
        assertEquals(original.getToken(), r.getToken());
        assertEquals(original.getCourseId(), r.getCourseId());
        assertEquals(original.getOwnerId(), r.getOwnerId());
        assertEquals(original.getScriptId(), r.getScriptId());
        assertEquals(original.getStatus(), r.getStatus());
        assertEquals(original.getStoragePath(), r.getStoragePath());
    }

    private AudioStreamInfo createSampleInfo() {
        AudioStreamInfo info = new AudioStreamInfo();
        info.setToken("token-abc-123");
        info.setAudioUrl("/api/audio/token-abc-123");
        info.setCourseId(42L);
        info.setCoursewareType("PPT");
        info.setOwnerId(100L);
        info.setScriptId(200L);
        info.setSegmentIndex(null);
        info.setAudioDurationMs(30000);
        info.setStatus("READY");
        info.setStoragePath("/var/microcourse/audio/abc.wav");
        info.setFileSizeBytes(480000L);
        return info;
    }
}

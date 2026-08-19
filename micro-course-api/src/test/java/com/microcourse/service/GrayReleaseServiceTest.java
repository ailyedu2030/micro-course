package com.microcourse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.enums.FeatureFlag;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GrayReleaseService 单元测试 (F10-D2 Phase 9)
 *
 * <p>覆盖 fail-closed 行为 + Redis 错误处理 + 5s 缓存语义。</p>
 */
@DisplayName("GrayReleaseService 单元测试")
class GrayReleaseServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;

    private ObjectMapper objectMapper;
    private GrayReleaseService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        objectMapper = new ObjectMapper();
        service = new GrayReleaseService(redisTemplate, objectMapper);
    }

    // ===== isFeatureEnabled =====

    @Test
    @DisplayName("isFeatureEnabled: Redis 返回 enabled=true → 返回 true")
    void isFeatureEnabled_true() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("MICRO_SPECIALTY_CLASS_IMPORT", true);
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        assertTrue(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: Redis 返回 enabled=false → 返回 false")
    void isFeatureEnabled_false() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("MICRO_SPECIALTY_CLASS_IMPORT", false);
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        assertFalse(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: Redis 无 key → fail-closed (false)")
    void isFeatureEnabled_emptyRedis() {
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY)).thenReturn(null);

        assertFalse(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: Redis 返回非法 JSON → fail-closed (warn 异常)")
    void isFeatureEnabled_invalidJson() {
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY)).thenReturn("not valid json{");

        assertFalse(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: Redis 异常 → fail-closed (返回 false)")
    void isFeatureEnabled_redisError() {
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertFalse(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: 未知 flag 名 → 跳过 + 返回 false (不抛异常)")
    void isFeatureEnabled_unknownFlag() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("SOMETHING_NOT_IN_ENUM", true);  // 不在 FeatureFlag 枚举中
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        // 即使有未知 flag, 我们的 flag 仍应返回 false
        assertFalse(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("isFeatureEnabled: null flag 入参 → 返回 false (fail-closed)")
    void isFeatureEnabled_nullInput() {
        assertFalse(service.isFeatureEnabled(null));
    }

    @Test
    @DisplayName("isFeatureEnabled: 5s 缓存生效 (第二次查询不打 Redis)")
    void isFeatureEnabled_cache5s() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("MICRO_SPECIALTY_CLASS_IMPORT", true);
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        // 第一次查询
        assertTrue(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
        // 第二次查询应走缓存
        assertTrue(service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));

        // 验证: 即使配置 mock 每次返回 true, Redis 也只被调用 1 次
        verify(valueOps, times(1)).get(GrayReleaseService.FEATURE_FLAG_KEY);
    }

    @Test
    @DisplayName("isFeatureEnabled: invalidateCache 后重新查询")
    void isFeatureEnabled_invalidateCache() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("MICRO_SPECIALTY_CLASS_IMPORT", true);
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT);
        service.invalidateCache();
        service.isFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT);

        // 失效缓存后重新查询 → 2 次 Redis 调用
        verify(valueOps, times(2)).get(GrayReleaseService.FEATURE_FLAG_KEY);
    }

    // ===== isGrayUser =====

    @Test
    @DisplayName("isGrayUser: 用户在白名单 → true")
    void isGrayUser_inWhitelist() {
        Set<String> members = new LinkedHashSet<>();
        members.add("100");
        members.add("200");
        when(setOps.members(GrayReleaseService.GRAY_USERS_KEY)).thenReturn(members);

        assertTrue(service.isGrayUser(100L));
        assertTrue(service.isGrayUser(200L));
    }

    @Test
    @DisplayName("isGrayUser: 用户不在白名单 → false")
    void isGrayUser_notInWhitelist() {
        Set<String> members = new LinkedHashSet<>();
        members.add("100");
        when(setOps.members(GrayReleaseService.GRAY_USERS_KEY)).thenReturn(members);

        assertFalse(service.isGrayUser(999L));
    }

    @Test
    @DisplayName("isGrayUser: Redis 无 key → fail-closed (false)")
    void isGrayUser_emptyRedis() {
        when(setOps.members(GrayReleaseService.GRAY_USERS_KEY)).thenReturn(null);

        assertFalse(service.isGrayUser(100L));
    }

    @Test
    @DisplayName("isGrayUser: Redis 异常 → fail-closed (false)")
    void isGrayUser_redisError() {
        when(setOps.members(GrayReleaseService.GRAY_USERS_KEY))
                .thenThrow(new RuntimeException("Redis timeout"));

        assertFalse(service.isGrayUser(100L));
    }

    @Test
    @DisplayName("isGrayUser: 无效 userId 格式 → 跳过 + 返回 false")
    void isGrayUser_invalidUserIdFormat() {
        Set<String> members = new LinkedHashSet<>();
        members.add("not-a-number");
        members.add("100");
        when(setOps.members(GrayReleaseService.GRAY_USERS_KEY)).thenReturn(members);

        assertFalse(service.isGrayUser(999L));  // 不在白名单
        assertTrue(service.isGrayUser(100L));  // 在白名单 (虽然有 invalid)
    }

    @Test
    @DisplayName("isGrayUser: null userId → false (不抛异常)")
    void isGrayUser_nullInput() {
        assertFalse(service.isGrayUser(null));
    }

    // ===== assertFeatureEnabled =====

    @Test
    @DisplayName("assertFeatureEnabled: 启用 → 不抛异常")
    void assertFeatureEnabled_ok() throws JsonProcessingException {
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("MICRO_SPECIALTY_CLASS_IMPORT", true);
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY))
                .thenReturn(objectMapper.writeValueAsString(flags));

        assertDoesNotThrow(() ->
                service.assertFeatureEnabled(FeatureFlag.MICRO_SPECIALTY_CLASS_IMPORT));
    }

    @Test
    @DisplayName("assertFeatureEnabled: 禁用 → 抛 FEATURE_DISABLED (HTTP 503)")
    void assertFeatureEnabled_throws() {
        when(valueOps.get(GrayReleaseService.FEATURE_FLAG_KEY)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertFeatureEnabled(FeatureFlag.NEW_PAYMENT_FLOW));
        assertEquals(ErrorCode.FEATURE_DISABLED.getCode(), ex.getCode());
    }
}
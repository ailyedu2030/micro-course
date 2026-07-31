package com.microcourse.service;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.AuthQueryServiceImpl;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthQueryServiceImpl 安全状态 fail-closed 测试。
 *
 * <p>覆盖：
 * <ol>
 *   <li>getLoginFailureCount Redis 故障且本地无缓存 → 503 抛出而非返回 0</li>
 *   <li>getLoginFailureCount Redis 故障但本地有缓存 → 返回缓存值</li>
 *   <li>getRefreshCount Redis 故障且本地无缓存 → 503 抛出而非返回 0</li>
 *   <li>getRefreshCount Redis 故障但本地有缓存 → 返回缓存值</li>
 * </ol>
 */
@DisplayName("AuthQueryServiceImpl — security check fail-closed")
class AuthQueryServiceFailClosedTest {

    private UserRepository userRepository;
    private RedisUtil redisUtil;
    private AuthQueryService queryService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisUtil = mock(RedisUtil.class);
        queryService = new AuthQueryServiceImpl(userRepository, redisUtil);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getLoginFailureCount: Redis 故障且本地无缓存 → 抛出 503（fail-closed）")
    void getLoginFailureCountWhenRedisDownAndNoLocalCacheThrows503() {
        when(redisUtil.getLoginFailureCount("testuser"))
                .thenThrow(new RuntimeException("Redis connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.getLoginFailureCount("testuser"));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("认证服务暂时不可用"));
    }

    @Test
    @DisplayName("getLoginFailureCount: Redis 故障但本地有缓存 → 返回缓存值（graceful degrade）")
    void getLoginFailureCountWhenRedisDownButLocalCacheReturnsCachedValue() {
        // 所有 Redis 调用都抛出异常，模拟完全不可用
        when(redisUtil.getLoginFailureCount(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(redisUtil.incrLoginFailure(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // incrLoginFailureQuietly 会捕获异常并写入本地缓存
        queryService.incrLoginFailureQuietly("testuser");
        queryService.incrLoginFailureQuietly("testuser");
        queryService.incrLoginFailureQuietly("testuser");

        // 现在 getLoginFailureCount 应返回本地缓存值（3）
        int count = queryService.getLoginFailureCount("testuser");
        assertEquals(3, count, "本地缓存应累积失败次数");
    }

    @Test
    @DisplayName("getRefreshCount: Redis 故障且本地无缓存 → 抛出 503（fail-closed）")
    void getRefreshCountWhenRedisDownAndNoLocalCacheThrows503() {
        when(redisUtil.getRefreshCount("192.168.1.1"))
                .thenThrow(new RuntimeException("Redis connection refused"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> queryService.getRefreshCount("192.168.1.1"));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getRefreshCount: Redis 故障但本地有缓存 → 返回缓存值（graceful degrade）")
    void getRefreshCountWhenRedisDownButLocalCacheReturnsCachedValue() {
        when(redisUtil.getRefreshCount("192.168.1.1"))
                .thenThrow(new RuntimeException("Redis connection refused"));
        when(redisUtil.incrRefreshCount(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // incrRefreshCountQuietly 会捕获异常并写入本地缓存
        queryService.incrRefreshCountQuietly("192.168.1.1");

        int count = queryService.getRefreshCount("192.168.1.1");
        assertEquals(1, count, "本地缓存应累积 refresh 次数");
    }

    @Test
    @DisplayName("Redis 正常时 getLoginFailureCount 委托给 RedisUtil 返回准确值")
    void getLoginFailureCountWhenRedisOkDelegatesToRedisUtil() {
        when(redisUtil.getLoginFailureCount("testuser")).thenReturn(3);

        int count = queryService.getLoginFailureCount("testuser");

        assertEquals(3, count);
        verify(redisUtil).getLoginFailureCount("testuser");
    }
}

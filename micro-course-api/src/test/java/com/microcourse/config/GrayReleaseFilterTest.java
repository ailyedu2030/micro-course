package com.microcourse.config;

import com.microcourse.service.GrayReleaseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * GrayReleaseFilter 单元测试 (F10-D2 Phase 9)
 *
 * <p>验证 HTTP request attribute 注入 + fail-closed 行为。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrayReleaseFilter 单元测试")
class GrayReleaseFilterTest {

    @Mock private GrayReleaseService grayReleaseService;
    @Mock private FilterChain filterChain;

    private GrayReleaseFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GrayReleaseFilter(grayReleaseService);
    }

    @AfterEach
    void cleanup() {
        // MockedStatic 上下文隔离: 每次测试结束后清空 SecurityContextHolder
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("已登录用户且在白名单 → 注入 gray.isGrayUser=true")
    void loggedInUser_inWhitelist() throws ServletException, IOException {
        // 模拟已登录用户 userId=100
        mockUserId(100L);
        when(grayReleaseService.isGrayUser(100L)).thenReturn(true);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // 调用私有方法
        invokeDoFilter(req, resp, chain);

        assertEquals(Boolean.TRUE, req.getAttribute(GrayReleaseFilter.ATTR_IS_GRAY_USER));
        assertEquals(100L, req.getAttribute(GrayReleaseFilter.ATTR_USER_ID));
    }

    @Test
    @DisplayName("已登录用户但不在白名单 → 注入 gray.isGrayUser=false")
    void loggedInUser_notInWhitelist() throws ServletException, IOException {
        mockUserId(200L);
        when(grayReleaseService.isGrayUser(200L)).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        invokeDoFilter(req, resp, chain);

        assertEquals(Boolean.FALSE, req.getAttribute(GrayReleaseFilter.ATTR_IS_GRAY_USER));
        assertEquals(200L, req.getAttribute(GrayReleaseFilter.ATTR_USER_ID));
    }

    @Test
    @DisplayName("未登录请求 → 注入 gray.isGrayUser=false (不查 Redis)")
    void anonymousRequest() throws ServletException, IOException {
        // 不 mock SecurityContext → SecurityUtil.getCurrentUserIdOpt() 返回 null
        // grayReleaseService 不应被调用
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        invokeDoFilter(req, resp, chain);

        assertEquals(Boolean.FALSE, req.getAttribute(GrayReleaseFilter.ATTR_IS_GRAY_USER));
        assertNull(req.getAttribute(GrayReleaseFilter.ATTR_USER_ID));
        verify(grayReleaseService, never()).isGrayUser(anyLong());
    }

    @Test
    @DisplayName("Redis 异常 → fail-closed (gray.isGrayUser=false, 继续执行 chain)")
    void redisError_failClosed() throws ServletException, IOException {
        mockUserId(300L);
        when(grayReleaseService.isGrayUser(300L))
                .thenThrow(new RuntimeException("Redis connection refused"));

        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // 不应抛异常, chain 应继续执行
        assertDoesNotThrow(() -> invokeDoFilter(req, resp, chain));
        assertEquals(Boolean.FALSE, req.getAttribute(GrayReleaseFilter.ATTR_IS_GRAY_USER));
        // chain.doFilter 仍被调用
        assertEquals(req, chain.getRequest());
    }

    /**
     * 模拟 SecurityUtil.getCurrentUserIdOpt() 返回指定 userId
     */
    private void mockUserId(Long userId) {
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        userId, null,
                        java.util.Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);
    }

    /**
     * 反射调用 protected doFilterInternal
     */
    private void invokeDoFilter(MockHttpServletRequest req, MockHttpServletResponse resp, MockFilterChain chain)
            throws ServletException, IOException {
        try {
            java.lang.reflect.Method method = GrayReleaseFilter.class
                    .getDeclaredMethod("doFilterInternal",
                            jakarta.servlet.http.HttpServletRequest.class,
                            jakarta.servlet.http.HttpServletResponse.class,
                            jakarta.servlet.FilterChain.class);
            method.setAccessible(true);
            method.invoke(filter, req, resp, chain);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ServletException) throw (ServletException) cause;
            if (cause instanceof IOException) throw (IOException) cause;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new RuntimeException(cause);
        }
    }
}
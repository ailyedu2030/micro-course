package com.microcourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JwtAuthenticationFilter Redis fail-closed")
class JwtAuthenticationFilterFailClosedTest {

    private JwtUtil jwtUtil;
    private RedisUtil redisUtil;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtUtil = mock(JwtUtil.class);
        redisUtil = mock(RedisUtil.class);
        filter = new JwtAuthenticationFilter(jwtUtil, redisUtil, new ObjectMapper());
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("访问 token 黑名单校验异常时必须返回 401/1008 而不是继续放行")
    void shouldRejectRequestWhenTokenBlacklistCheckFails() throws Exception {
        request.addHeader("Authorization", "Bearer access-token");

        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.getJtiFromToken("access-token")).thenReturn("access-jti");
        when(jwtUtil.getUserIdFromToken("access-token")).thenReturn(9L);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单校验暂时不可用，请稍后重试"))
                .when(redisUtil).isTokenBlacklisted("access-jti");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":1008"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(filterChain.getRequest());
    }

    @Test
    @DisplayName("访问用户级黑名单校验异常时必须返回 401/1008 而不是继续放行")
    void shouldRejectRequestWhenUserTokenBlacklistCheckFails() throws Exception {
        request.addHeader("Authorization", "Bearer access-token");

        when(jwtUtil.validateToken("access-token")).thenReturn(true);
        when(jwtUtil.getJtiFromToken("access-token")).thenReturn("access-jti");
        when(jwtUtil.getUserIdFromToken("access-token")).thenReturn(9L);
        when(redisUtil.isTokenBlacklisted("access-jti")).thenReturn(false);
        doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Token 黑名单校验暂时不可用，请稍后重试"))
                .when(redisUtil).isUserTokenBlacklisted(9L);

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":1008"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(filterChain.getRequest());
    }
}

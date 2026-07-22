package com.microcourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.enums.UserRole;
import com.microcourse.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.microcourse.entity.User;

/**
 * ApiKeyAuthenticationFilter 单元测试
 *
 * <p>覆盖六大行为：
 * <ol>
 *   <li>X-API-Key 有效 + TEACHER → SecurityContext 设置，filterChain 继续</li>
 *   <li>X-API-Key 有效 + ADMIN → SecurityContext 设置，filterChain 继续</li>
 *   <li>X-API-Key 有效 + STUDENT → 403 拒绝（API Key 仅限教师/管理员）</li>
 *   <li>X-API-Key 无效（找不到）→ 401 返回 21001</li>
 *   <li>无 X-API-Key 头 → 放行（filterChain 继续，不设置 SecurityContext）</li>
 *   <li>DB 异常 → fail-safe 401 返回 21001</li>
 * </ol>
 */
@DisplayName("ApiKeyAuthenticationFilter — X-API-Key 认证过滤器")
class ApiKeyAuthenticationFilterTest {

    private ApiKeyAuthenticationFilter filter;
    private UserRepository userRepository;
    private ObjectMapper objectMapper;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        objectMapper = new ObjectMapper();
        filter = new ApiKeyAuthenticationFilter(userRepository, objectMapper);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ───────────────────── helpers ─────────────────────

    private User teacherUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("teacher1");
        u.setRole(UserRole.TEACHER);
        return u;
    }

    private User adminUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("admin");
        u.setRole(UserRole.ADMIN);
        return u;
    }

    private User studentUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("student1");
        u.setRole(UserRole.STUDENT);
        return u;
    }

    // ───────────────────── tests ─────────────────────

    @Test
    @DisplayName("有效 X-API-Key + TEACHER → SecurityContext 设置，请求放行")
    void validApiKeyWithTeacherSetsSecurityContextAndPasses() throws Exception {
        when(userRepository.findByApiKey("valid-teacher-key")).thenReturn(Optional.of(teacherUser(10L)));
        request.addHeader("X-API-Key", "valid-teacher-key");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "SecurityContext 应被设置");
        assertEquals(10L, auth.getPrincipal(), "principal 应为 userId");
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_TEACHER")), "应有 ROLE_TEACHER 权限");
        assertEquals(200, response.getStatus(), "响应状态应为 200（放行）");
        assertNotNull(filterChain.getRequest(), "filterChain 应被调用");
        verify(userRepository).findByApiKey("valid-teacher-key");
    }

    @Test
    @DisplayName("有效 X-API-Key + ADMIN → SecurityContext 设置，请求放行")
    void validApiKeyWithAdminSetsSecurityContextAndPasses() throws Exception {
        when(userRepository.findByApiKey("valid-admin-key")).thenReturn(Optional.of(adminUser(1L)));
        request.addHeader("X-API-Key", "valid-admin-key");

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(1L, auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest());
    }

    @Test
    @DisplayName("有效 X-API-Key + STUDENT → 403 拒绝")
    void validApiKeyWithStudentReturnsForbidden() throws Exception {
        when(userRepository.findByApiKey("student-key")).thenReturn(Optional.of(studentUser(99L)));
        request.addHeader("X-API-Key", "student-key");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "SecurityContext 不应设置");
        assertEquals(403, response.getStatus(), "响应状态应为 403");
        assertNull(filterChain.getRequest(), "STUDENT 角色应被拦截，filterChain 不应被调用");
        String body = response.getContentAsString();
        assertTrue(body.contains("\"code\":"), "响应应为 JSON");
        assertTrue(body.contains("API Key 仅限教师或管理员使用") || body.contains("NO_PERMISSION"),
                "错误信息应提及权限问题");
    }

    @Test
    @DisplayName("无效 X-API-Key（不存在）→ 401 返回 21001")
    void invalidApiKeyReturnsUnauthorized() throws Exception {
        when(userRepository.findByApiKey("invalid-key")).thenReturn(Optional.empty());
        request.addHeader("X-API-Key", "invalid-key");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus(), "响应状态应为 401");
        String body = response.getContentAsString();
        assertTrue(body.contains("21001"), "错误码应为 21001");
        assertTrue(body.contains("无效的 Hermes API Key") || body.contains("Hermes API Key"),
                "错误信息应说明 API Key 无效");
    }

    @Test
    @DisplayName("无 X-API-Key 头 → 放行，SecurityContext 不设置")
    void missingApiKeyPassesThroughWithoutSettingSecurityContext() throws Exception {
        // 不设置 X-API-Key header
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "无 API Key 时 SecurityContext 不应设置");
        assertEquals(200, response.getStatus(), "响应状态应为 200（放行）");
        assertNotNull(filterChain.getRequest(), "filterChain 应被调用");
        verify(userRepository, never()).findByApiKey(anyString());
    }

    @Test
    @DisplayName("空 X-API-Key 头 → 放行（视为无 header）")
    void blankApiKeyPassesThrough() throws Exception {
        request.addHeader("X-API-Key", "   ");
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        assertNotNull(filterChain.getRequest());
        verify(userRepository, never()).findByApiKey(anyString());
    }

    @Test
    @DisplayName("DB 异常 → fail-safe 401 返回 21001，不阻断请求链路")
    void dbExceptionReturnsFailSafeUnauthorized() throws Exception {
        when(userRepository.findByApiKey("db-error-key"))
                .thenThrow(new RuntimeException("connection refused"));
        request.addHeader("X-API-Key", "db-error-key");

        filter.doFilterInternal(request, response, filterChain);

        assertEquals(401, response.getStatus(), "fail-safe: DB 异常时返回 401 而非抛出不降级");
        String body = response.getContentAsString();
        assertTrue(body.contains("21001"), "错误码应为 21001");
    }

    @Test
    @DisplayName("X-API-Key trim 处理 — 前后空格应被忽略")
    void apiKeyIsTrimmedBeforeLookup() throws Exception {
        when(userRepository.findByApiKey("valid-teacher-key")).thenReturn(Optional.of(teacherUser(10L)));
        request.addHeader("X-API-Key", "  valid-teacher-key  ");

        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "trim 后的 key 应能匹配");
        assertEquals(200, response.getStatus());
        verify(userRepository).findByApiKey("valid-teacher-key");
    }
}

package com.microcourse.plugin.interactive;

import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.exception.GlobalExceptionHandler;
import com.microcourse.plugin.interactive.filter.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler + TraceIdFilter 单元测试.
 *
 * 【BUG #30 / #31 修复验证】
 * - BusinessException → 400 + 业务消息
 * - AccessDeniedException → 403
 * - Exception 兜底 → 500 + traceId (不泄露堆栈)
 * - TraceIdFilter → MDC 注入 + X-Trace-Id header
 *
 * 注: 不直接读 R.code (无 getter), 测 HTTP status + body.toString() 含关键文本.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private TraceIdFilter traceIdFilter;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        traceIdFilter = new TraceIdFilter();
        MDC.clear();
    }

    @Test
    @DisplayName("BusinessException → 400 BAD_REQUEST, code+message 透传")
    void businessException400() {
        BusinessException ex = new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "PPT page not found: 99");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/courses/1/ppt/pages/99");

        ResponseEntity<R<Object>> resp = handler.handleBusinessException(ex, req);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        R<Object> body = resp.getBody();
        // R 有 getMessage(), 没 getCode(), 但 toString() 输出含 message
        assertEquals("PPT page not found: 99", body.getMessage());
    }

    @Test
    @DisplayName("AccessDeniedException → 403 FORBIDDEN, 不泄露详情")
    void accessDenied403() {
        org.springframework.security.access.AccessDeniedException ex =
                new org.springframework.security.access.AccessDeniedException("user not admin");
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/courses/1/ppt/pages");

        ResponseEntity<R<Object>> resp = handler.handleAccessDenied(ex, req);

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        String msg = resp.getBody().getMessage();
        // 响应消息不包含具体堆栈信息
        assertFalse(msg.contains("user not admin"),
                "403 响应不应泄露 AccessDeniedException 原始消息 (P0 安全)");
        assertTrue(msg.contains("Access denied"));
    }

    @Test
    @DisplayName("兜底 Exception → 500 + traceId, 不泄露堆栈 (P0 安全)")
    void uncaughtException500WithTraceId() {
        MDC.put("traceId", "test-trace-abc123");
        try {
            RuntimeException ex = new RuntimeException("SQL: SELECT * FROM secrets WHERE password='xxx'");
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/courses/1/courseware/99");

            ResponseEntity<R<Object>> resp = handler.handleAny(ex, req);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
            String body = resp.getBody().getMessage();
            // 【P0 安全】 不泄露 SQL/堆栈
            assertFalse(body.contains("SELECT"), "500 响应不应泄露 SQL 内容: body=" + body);
            assertFalse(body.contains("secrets"), "500 响应不应泄露表名: body=" + body);
            assertFalse(body.contains("password='"), "500 响应不应泄露字段值: body=" + body);
            // 但应含 traceId (供用户报告)
            assertTrue(body.contains("test-trace-abc123"),
                    "500 响应应含 traceId 供用户反馈: body=" + body);
        } finally {
            MDC.remove("traceId");
        }
    }

    @Test
    @DisplayName("TraceIdFilter: 自动生成 traceId 并注入 MDC + 响应 header")
    void traceIdFilterGeneratesAndPropagates() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/courses/1/courseware/99");
        MockHttpServletResponse res = new MockHttpServletResponse();

        final String[] captured = {null};
        traceIdFilter.doFilter(req, res, (request, response) -> {
            // 在 filter 链内, MDC 应已被注入
            String mdcTraceId = MDC.get("traceId");
            captured[0] = mdcTraceId;
            assertNotNull(mdcTraceId, "MDC traceId 在 filter 内必须存在");
            assertEquals(16, mdcTraceId.length(), "自动生成 traceId 长度 16");
            assertTrue(mdcTraceId.matches("[0-9a-f]{16}"));
        });

        // 响应 header 应含 X-Trace-Id (与 MDC 一致)
        assertEquals(captured[0], res.getHeader("X-Trace-Id"));
        // MDC 应在 finally 清除
        assertNull(MDC.get("traceId"), "MDC 应在请求结束后清除, 防内存泄漏");
    }

    @Test
    @DisplayName("TraceIdFilter: 上游传入 X-Trace-Id 时沿用, 不重新生成")
    void traceIdFilterPreservesUpstream() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/courses/1/courseware/99");
        req.addHeader("X-Trace-Id", "upstream-trace-xyz789");
        MockHttpServletResponse res = new MockHttpServletResponse();

        traceIdFilter.doFilter(req, res, (request, response) -> {
            assertEquals("upstream-trace-xyz789", MDC.get("traceId"));
        });

        assertEquals("upstream-trace-xyz789", res.getHeader("X-Trace-Id"));
    }
}
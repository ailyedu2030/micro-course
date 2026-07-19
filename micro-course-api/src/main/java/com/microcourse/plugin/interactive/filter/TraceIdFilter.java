package com.microcourse.plugin.interactive.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * 【BUG #31 部分实现】 TraceId 链路追踪 Filter.
 *
 * <p>
 * 每个请求自动生成 traceId (UUID 前 16 字符), 注入:
 * <ul>
 *   <li>SLF4J MDC → 所有 log 自动含 traceId</li>
 *   <li>HTTP 响应 header X-Trace-Id → 前端可读</li>
 *   <li>上游若传 X-Trace-Id header, 沿用 (分布式追踪)</li>
 * </ul>
 *
 * <p>
 * 配合 Sleuth/Zipkin 可做完整 APM. 当前简化版仅本地追踪.
 * </p>
 *
 * <p>
 * 优先级: 最高 (Ordered.HIGHEST_PRECEDENCE), 在所有业务 filter 之前.
 * </p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String traceId = req.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.length() > 64 || traceId.isBlank()) {
            // 上游未传 → 自动生成 (UUID 前 16 字符足够)
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        try {
            MDC.put(MDC_KEY, traceId);
            res.setHeader(TRACE_ID_HEADER, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
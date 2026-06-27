package com.microcourse.audit;

import com.microcourse.entity.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@link AuditedLog} 运行期审计拦截器（P2-10）。
 *
 * <p>在 Spring MVC 处理链中，对标注了 {@link AuditedLog} 的 Controller 方法，
 * 于请求完成后（{@code afterCompletion}）收集操作信息并交由 {@link AuditLogWriter}
 * 异步写入 {@code operation_logs} 表，统一覆盖此前仅靠 {@code isAuthenticated()}
 * 兜底、无审计记录的端点。</p>
 *
 * <p><b>无注解放行：</b>未标注 {@link AuditedLog} 的 handler 在 {@code preHandle}
 * 与 {@code afterCompletion} 首行即返回，对其余所有端点零影响。</p>
 *
 * <p><b>UX 零退化（与 {@link AuditLogWriter} 协同）：</b></p>
 * <ul>
 *   <li>{@code afterCompletion} 在响应已生成之后执行，不改变响应内容与契约。</li>
 *   <li>全过程被 try-catch 包裹，审计路径任何异常都不会抛回主流程。</li>
 *   <li>用户标识在请求线程内（{@code SecurityContext} 仍有效）同步取出后随实体传出，
 *       异步线程不再访问 {@code SecurityContext}。</li>
 *   <li>实际写库通过 {@link AuditLogWriter#write} 异步执行，不占用请求响应时间。</li>
 * </ul>
 *
 * <p>采用纯 Spring MVC {@code HandlerInterceptor}（与 {@code RequireRoleInterceptor}
 * 一致），不依赖 AspectJ，符合"不引入新依赖"约束。</p>
 */
public class AuditedLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditedLogInterceptor.class);

    /** 请求级起始时间标记，用于计算处理耗时 */
    private static final String ATTR_START = "com.microcourse.audit.startTime";

    private static final int MAX_ACTION_LEN = 100;
    private static final int MAX_DETAIL_LEN = 500;
    private static final int MAX_IP_LEN = 64;
    private static final int MAX_UA_LEN = 255;

    private final AuditLogWriter auditLogWriter;

    public AuditedLogInterceptor(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 仅对带 @AuditedLog 的 handler 记录起始时间；其余请求透传，零开销
        if (handler instanceof HandlerMethod hm && hm.getMethodAnnotation(AuditedLog.class) != null) {
            request.setAttribute(ATTR_START, System.currentTimeMillis());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                return;
            }
            AuditedLog annotation = handlerMethod.getMethodAnnotation(AuditedLog.class);
            if (annotation == null) {
                // 未标注 @AuditedLog，交由其他机制处理，本拦截器不介入
                return;
            }

            boolean ok = (ex == null && response.getStatus() < 400);

            OperationLog entry = new OperationLog();
            entry.setUserId(currentUserIdOrNull());
            entry.setAction(truncate(annotation.value(), MAX_ACTION_LEN));
            entry.setTargetType(handlerMethod.getBeanType().getSimpleName());
            entry.setSuccess(ok);
            entry.setIp(truncate(resolveClientIp(request), MAX_IP_LEN));
            entry.setUserAgent(truncate(request.getHeader("User-Agent"), MAX_UA_LEN));
            entry.setDurationMs(resolveDurationMs(request));
            entry.setDetail(truncate(buildDetail(request, handlerMethod, response.getStatus(), ex), MAX_DETAIL_LEN));

            // fire-and-forget：异步写入，不阻塞主流程
            auditLogWriter.write(entry);
        } catch (Exception logEx) {
            // 审计路径绝不影响主流程：仅记录，不抛出
            log.warn("[AuditLog] 构造操作日志失败（已忽略，不影响主流程）", logEx);
        }
    }

    /**
     * 在当前（请求）线程内安全读取登录用户 ID，未登录返回 {@code null}。
     * principal 由 JwtAuthenticationFilter 写入，类型为 {@link Long}。
     */
    private static Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long l) {
            return l;
        }
        if (principal instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    /** 计算处理耗时（毫秒）；无起始标记时返回 {@code null} */
    private static Integer resolveDurationMs(HttpServletRequest request) {
        Object start = request.getAttribute(ATTR_START);
        if (start instanceof Long s) {
            long d = System.currentTimeMillis() - s;
            if (d >= 0 && d <= Integer.MAX_VALUE) {
                return (int) d;
            }
        }
        return null;
    }

    /** 解析客户端 IP：优先取代理头第一个地址，回退到 remoteAddr */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }

    /** 构造 detail JSON：方法标识 + 请求路径 + HTTP 状态（+ 异常类名） */
    private static String buildDetail(HttpServletRequest request, HandlerMethod handlerMethod,
                                      int status, Exception ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"method\":\"").append(handlerMethod.getBeanType().getSimpleName())
                .append('.').append(handlerMethod.getMethod().getName());
        sb.append("\",\"path\":\"").append(request.getMethod()).append(' ').append(request.getRequestURI());
        sb.append("\",\"status\":").append(status);
        if (ex != null) {
            sb.append(",\"error\":\"").append(ex.getClass().getSimpleName()).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}

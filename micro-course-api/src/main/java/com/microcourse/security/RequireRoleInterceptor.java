package com.microcourse.security;

import com.microcourse.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * {@link RequireRole} 运行期校验拦截器（P3-6）。
 *
 * <p>在 Spring Security 过滤器链之后、Controller 方法执行之前运行，读取目标
 * {@link HandlerMethod} 上的 {@link RequireRole}（方法级优先，回退类级），
 * 复用 {@link SecurityUtil#hasRole(String)} 判定当前用户是否命中任一允许角色。
 * 校验失败抛出 {@link AccessDeniedException}，由
 * {@code GlobalExceptionHandler#handleAccessDenied} 统一转换为 HTTP 403。</p>
 *
 * <p><b>等价性与零退化：</b>逻辑与 {@code @PreAuthorize("hasAnyRole(...)")} 等价。
 * 当本注解与 {@code @PreAuthorize} 叠加且角色集合一致时，{@code @PreAuthorize}
 * 在过滤器层先行决断；能通过 {@code @PreAuthorize} 的请求必然也能通过本拦截器，
 * 故本拦截器在叠加场景下不会成为新的拒绝者，对既有行为零影响。</p>
 *
 * <p><b>无注解放行：</b>未标注 {@link RequireRole} 的 handler 直接放行，由原有
 * 安全机制（{@code @PreAuthorize} / SecurityFilterChain）负责，不改变任何现有端点。</p>
 *
 * <p>采用纯 Spring MVC {@code HandlerInterceptor} 实现，不依赖 AspectJ，符合
 * "不引入新依赖"约束。</p>
 */
public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 非 Controller 方法（如静态资源 ResourceHttpRequestHandler）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法级优先，回退到类级（TYPE 级注解）
        RequireRole annotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (annotation == null) {
            // 未使用 @RequireRole，交由其他安全机制处理
            return true;
        }

        String[] allowedRoles = annotation.value();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean hasRole = Arrays.stream(allowedRoles).anyMatch(SecurityUtil::hasRole);
        if (!hasRole) {
            throw new AccessDeniedException(
                    "Required role not found. Allowed: " + Arrays.toString(allowedRoles));
        }

        return true;
    }
}

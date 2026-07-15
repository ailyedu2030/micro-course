package com.microcourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.R;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API Key 认证过滤器（支持 Hermes/Trae 等内部服务使用 X-API-Key 直接调用微课平台 API）。
 *
 * <p>工作原理：
 * <ol>
 *   <li>从请求头读取 {@code X-API-Key}</li>
 *   <li>通过 UserRepository.findByApiKey(apiKey) 查找关联用户</li>
 *   <li>验证用户角色为 TEACHER 或 ADMIN</li>
 *   <li>设置 SecurityContext（与 JwtAuthenticationFilter 等价），使 @PreAuthorize 生效</li>
 *   <li>若 X-API-Key 无效，返回 401 JSON 错误（不触发下一个 Filter）</li>
 *   <li>若无 X-API-Key 头，放行让 JwtAuthenticationFilter 处理（JWT 优先）</li>
 * </ol>
 *
 * <p>过滤器顺序：在 JwtAuthenticationFilter 之前注册，确保 JWT 请求不受影响。
 * JwtAuthenticationFilter 只在有 Authorization: Bearer header 时处理，无 header 时直接放行。
 *
 * <p>级联生效：SecurityContext 设置后，UserStatusCheckFilter 会自动校验用户状态（禁用/删除用户会被拦截）。
 */
@Component
@Order(20)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("[ApiKey] X-API-Key present, validating...");
        Optional<User> callerOpt;
        try {
            callerOpt = userRepository.findByApiKey(apiKey.trim());
        } catch (Exception e) {
            log.warn("[ApiKey] DB error looking up API key: {}", e.getMessage());
            writeErrorResponse(response, ErrorCode.HERMES_INVALID_API_KEY);
            return;
        }

        if (callerOpt.isEmpty()) {
            log.warn("[ApiKey] API key not found or user inactive");
            writeErrorResponse(response, ErrorCode.HERMES_INVALID_API_KEY);
            return;
        }

        User caller = callerOpt.get();

        UserRole role = caller.getRole();
        if (role != UserRole.TEACHER && role != UserRole.ADMIN) {
            log.warn("[ApiKey] API key belongs to non-TEACHER/ADMIN role: userId={}, role={}", caller.getId(), role);
            writeErrorResponse(response, ErrorCode.NO_PERMISSION, "API Key 仅限教师或管理员使用");
            return;
        }

        List<SimpleGrantedAuthority> authorities;
        if (role.name().contains(",")) {
            authorities = Arrays.stream(role.name().split(","))
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                    .collect(Collectors.toList());
        } else {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(caller.getId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("[ApiKey] Authenticated via API key: userId={}, username={}, role={}",
                caller.getId(), caller.getUsername(), role);

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        writeErrorResponse(response, errorCode, errorCode.getMessage());
    }

    private void writeErrorResponse(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(errorCode.getCode(), message)));
    }
}

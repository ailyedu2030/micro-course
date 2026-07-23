package com.microcourse.config;

import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.R;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 *
 * 依据：
 * - Phase 1 业务逻辑 §2.4：JWT 认证过滤器 doFilterInternal 流程
 * - v1.6.0 P0-2：未认证返回 401 而非 403
 * - 深度审查：错误码对齐 ErrorCode 枚举（修复 11xxx 冲突）+ 响应补 timestamp 对齐 R<T> 契约
 */
@Component
@Order(30)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, 1005, "token格式错误");
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            writeErrorResponse(response, 1004, "token无效或已过期");
            return;
        }

        String jti = jwtUtil.getJtiFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        try {
            if (jti != null && redisUtil.isTokenBlacklisted(jti)) {
                writeErrorResponse(response, 1004, "token已失效,请重新登录");
                return;
            }

            // P1I-001: 用户级 Token 黑名单校验（禁用用户后批量作废所有 Token）
            if (redisUtil.isUserTokenBlacklisted(userId)) {
                writeErrorResponse(response, 1004, "账号已被禁用，请重新登录");
                return;
            }
        } catch (BusinessException e) {
            writeErrorResponse(response, e.getCode(), e.getMessage());
            return;
        }

        String roleStr = jwtUtil.getRoleFromTokenAsString(token);

        List<SimpleGrantedAuthority> authorities;
        if (roleStr != null && roleStr.contains(",")) {
            authorities = Arrays.stream(roleStr.split(","))
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                    .collect(Collectors.toList());
        } else {
            UserRole role = jwtUtil.getRoleFromToken(token);
            authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * 写入符合 R<T> 契约格式的错误响应（含 timestamp）
     * P2 #29 fix: 使用 ObjectMapper 序列化替代 String.format，避免特殊字符破坏 JSON 格式
     */
    private void writeErrorResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> error = R.fail(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}

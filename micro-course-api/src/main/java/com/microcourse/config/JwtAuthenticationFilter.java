package com.microcourse.config;

import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器
 *
 * 依据：
 * - Phase 1 业务逻辑 §2.4：JWT 认证过滤器 doFilterInternal 流程
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtUtil.validateToken(token)) {
                // 检查 token 是否在黑名单
                String jti = jwtUtil.getJtiFromToken(token);
                if (jti != null && redisUtil.isTokenBlacklisted(jti)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Long userId = jwtUtil.getUserIdFromToken(token);
                UserRole role = jwtUtil.getRoleFromToken(token);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
package com.microcourse.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

/**
 * Spring Security 配置
 *
 * P0-1: 放行 /api/videos/stream/** 以便 HLS 播放器直接访问
 * P0-3: 放行 /api/files/** 以便 img 标签加载封面
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final String[] corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                          @Value("${cors.allowed-origins}") String corsOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.corsAllowedOrigins = corsOrigins.split(",");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(perms -> perms.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        // login/cas: 公开; refresh: refreshToken 在 body 中作为凭证
                        .requestMatchers("/api/auth/login", "/api/auth/cas", "/api/auth/refresh").permitAll()
                        .requestMatchers("GET", "/api/departments/**").authenticated()
                        .requestMatchers("GET", "/api/majors/**").authenticated()
                        .requestMatchers("GET", "/api/classes/**").authenticated()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        .requestMatchers("/api-docs/**", "/swagger-ui/**").permitAll()
                        // P0-1: HLS 流式端点 — hls.js 通过 xhrSetup 携带 JWT，需认证
                        .requestMatchers("GET", "/api/videos/stream/**").authenticated()
                        // P0-3: 封面/文件资源端点（封面非敏感数据，img 标签无法携带 Auth 头）
                        .requestMatchers("GET", "/api/files/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(restAuthenticationEntryPoint));

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(corsAllowedOrigins));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

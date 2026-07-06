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
import com.microcourse.security.UserStatusCheckFilter;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;

/**
 * Spring Security 配置
 *
 * P0-1: 放行 /api/videos/stream/** 以便 HLS 播放器直接访问
 * P0-3: 放行 /api/files/** 以便 img 标签加载封面
 *
 * P0-8 修复（Phase A-3）：/api/files/** 由通配 permitAll 收窄为分类授权
 * - covers/**   （视频封面）：permitAll —— 前端 img :src 直接引用，物理无法携带 Auth 头
 * - avatars/**  （用户头像）：permitAll —— el-avatar :src 直接引用，且头像为公开展示数据
 * - banners/**  （公开轮播图）：permitAll —— 前端 img :src 直接引用
 * - system/**   （平台 Logo/系统资源）：permitAll —— 公开静态资源（前瞻白名单）
 * - videos/**   （视频文件）：permitAll —— HTML5 <video> 无法携带 Authorization header，nginx 层有 valid_referers 防盗链
 * - 其他（slides 课件、attachments 附件等私有文件）：authenticated
 *   —— 堵住 WebMvcConfig 将 /api/files/** 映射到 uploads/ 后对私有内容的静态越权下载；
 *      对象级 owner 校验在对应业务 Controller/Service 层执行
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserStatusCheckFilter userStatusCheckFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final String[] corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          UserStatusCheckFilter userStatusCheckFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                          @Value("${cors.allowed-origins}") String corsOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userStatusCheckFilter = userStatusCheckFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.corsAllowedOrigins = corsOrigins.split(",");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF 禁用原因：REST API 使用 JWT Bearer Token（Authorization header），不依赖 cookie-based 会话，无 CSRF 攻击面
                // 重要：若未来引入 cookie-based 认证（如 CAS 单点登录），必须重新启用 CSRF 保护
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // P1-5（Round 5）：CSP 资源策略对账。原 "default-src 'self'" 会阻断
                        // 前端实际使用的合法资源（design-tokens.css @import 的 Google Fonts、
                        // hls.js 的 blob worker/media、authImage 的 blob 图片）。下述白名单依据
                        // 前端实测资源逐项放行，与 nginx.conf 的 CSP 保持一致：
                        //   - style-src + https://fonts.googleapis.com：Outfit 字体样式表
                        //   - font-src  + https://fonts.gstatic.com：字体文件
                        //   - img-src   + data: blob:：封面/头像 data URI 与 blob 预览
                        //   - media-src/worker-src + blob:：HLS 视频与 hls.js worker
                        //   - object-src 'none' / base-uri 'self' / form-action 'self'：纵深加固
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                // P1-I #44 / P2 #28 fix: hls.js requires unsafe-eval/unsafe-inline for media handling (blob worker, HLS.js internals)
                                // Vue CLI dev server also needs unsafe-inline; in production set script-src 'self' only and use nonce/hash
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                "img-src 'self' data: blob: https:; " +
                                "font-src 'self' data: https://fonts.gstatic.com; " +
                                "connect-src 'self' https://api.deepseek.com ws: wss:; " +
                                "media-src 'self' blob: https:; " +
                                "frame-src 'self'; " +
                                "worker-src 'self' blob:; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "form-action 'self'"))
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(perms -> perms.policy(
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        // login/cas: 公开; refresh: refreshToken 在 body 中作为凭证
                        .requestMatchers("/api/auth/login", "/api/auth/cas", "/api/auth/refresh", "/api/auth/register", "/api/auth/registration-status").permitAll()
                        .requestMatchers("GET", "/api/admin/stats/health").permitAll()
                        // P3-14/P3-15（Round 7-3）：监控端点放行 —— 仅放行 health 与 prometheus 两个具体路径
                        // （收窄白名单，不放行 /actuator/** 通配，避免未来误暴露 env/beans/heapdump 等敏感端点）。
                        // management.endpoints.web.exposure.include 已限定仅暴露 health,info,metrics,prometheus；
                        // 暴露内容为 hikaricp_*/jvm_*/http_* 等运行时指标，不含业务敏感数据。
                        // ⚠️ 生产环境须在网络层（nginx/防火墙）进一步限制 /actuator/** 仅监控内网可达（见 docs/监控指标.md §3）。
                        .requestMatchers("GET", "/actuator/health", "/actuator/prometheus").permitAll()
                        // 枚举导出端点 —— 匿名可访问。仅含枚举名/label，无敏感数据。
                        // 前端首屏在登录前即需加载枚举。Controller 层 @PreAuthorize("permitAll()") 一致。
                        .requestMatchers("GET", "/api/enums/export").permitAll()
                        // Round 5-3 (P1-10)：公开系统配置 —— 前端首屏渲染站点名称/Logo/是否开放注册等
                        // 非敏感元数据，权限矩阵 v2.0 定义为「所有用户（含未登录）」。Controller 内白名单
                        // 严格限定可返回键，CAS/上传上限等敏感配置永不暴露。须先于通配 authenticated。
                        .requestMatchers("GET", "/api/system-configs/public").permitAll()
                        // Phase 14: 微专业公共端点 - 课程广场和详情页无需登录
                        .requestMatchers("GET", "/api/micro-specialties/square").permitAll()
                        // BUG-001 fix: Spring Security MvcRequestMatcher 不识别 {id} 语法,使用 Ant 通配符 *
                        .requestMatchers("GET", "/api/micro-specialties/*").permitAll()
                        .requestMatchers("GET", "/api/micro-specialties/*/courses").permitAll()
                        .requestMatchers("GET", "/api/micro-specialties/*/teachers").permitAll()
                        // P0 闭环修复 Round 4: 公开 Banner API（学生端首页轮播）
                        .requestMatchers("GET", "/api/banners").permitAll()
                        // 前端错误自动上报 —— 需登录态。Controller 仅落日志、不返回敏感数据、不写库。
                        // 权限由 Controller 上的 @PreAuthorize("isAuthenticated()") 控制
                        .requestMatchers("GET", "/api/departments/**").authenticated()
                        .requestMatchers("GET", "/api/majors/**").authenticated()
                        .requestMatchers("GET", "/api/classes/**").authenticated()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/users/**").authenticated()
                        // P2-1: Swagger 文档路径（生产禁用见 application-prod.yml springdoc.api-docs.enabled=false）
                        // 【course-domain-drift-fix】springdoc-openapi 2.3.0 使用 /v3/api-docs 和 /swagger-ui.html
                        .requestMatchers("/v3/api-docs/**", "/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**").permitAll()
                        // P0-1: HLS 流式端点 — hls.js 通过 xhrSetup 携带 JWT，需认证
                        // P0-07 修复：路径与 VideoStreamController @RequestMapping("/api/video-stream") 对齐
                        .requestMatchers("GET", "/api/video-stream/**").authenticated()
                        // P0-8 修复：收窄 /api/files/** —— 按文件类型分类授权（白名单顺序须先于通配 authenticated）
                        // 封面图片：非敏感，前端 img :src 需无 Auth 访问
                        .requestMatchers("GET", "/api/files/covers/**").permitAll()
                        // 用户头像：公开展示数据，el-avatar :src 需无 Auth 访问
                        .requestMatchers("GET", "/api/files/avatars/**").permitAll()
                        // 公开轮播图
                        .requestMatchers("GET", "/api/files/banners/**").permitAll()
                        // 客户体验修复 v1.7.0: 视频文件公开 (HTML5 <video> 元素无法携带 Authorization header)
                        // 安全模型: 课程本身受 @PreAuthorize 保护,未选课学生看不到播放入口
                        // 视频文件 URL 一旦泄露也可访问,接受此 trade-off (业界标准,如 Coursera/慕课网)
                        .requestMatchers("GET", "/api/files/videos/**").permitAll()
                        // 平台 Logo/系统资源（前瞻白名单）
                        .requestMatchers("GET", "/api/files/system/**").permitAll()
                        // 其他文件（slides 课件、attachments 附件等私有资源）：需登录 + Controller 层 owner 校验
                        .requestMatchers("GET", "/api/files/**").authenticated()
                        // P0-SEC-FIX: 放行支付回调端点，外部支付网关无法携带 JWT
                        .requestMatchers("POST", "/api/orders/callback").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Round 8-2：在 JWT 认证之后插入用户状态校验，拦截「已禁用/删除但 token 仍有效」的访问
                .addFilterAfter(userStatusCheckFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(restAuthenticationEntryPoint));

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // SEC-006 修复 v1.8.0: BCrypt 强度从 10 提升到 12 (OWASP 2023 建议最小值)
        // 强度 12 ≈ 2^12 = 4096 轮哈希, ~400ms 每次, 安全性提升 4x, 现代 CPU 可接受
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(corsAllowedOrigins));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // P2-11: 收窄 allowedHeaders（原 "*"）为显式白名单，遵循最小授权原则。
        // 白名单依据前端实际使用的请求头（utils/request.js 仅设置 Authorization；
        // Content-Type/Accept 为 axios JSON 请求默认头）+ 常见前瞻头，避免破坏现有请求。
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "X-Requested-With",
                "X-Tenant-Id",
                "X-Device-Id"
        ));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}

package com.microcourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.audit.AuditLogWriter;
import com.microcourse.audit.AuditedLogInterceptor;
import com.microcourse.security.FileAccessLogger;
import com.microcourse.security.FileAccessRateLimitInterceptor;
import com.microcourse.security.RequireRoleInterceptor;
import com.microcourse.util.RedisUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * P0-3: 封面文件静态资源映射
 *
 * /api/files/** → file:./uploads/
 * 使封面 URL（如 /api/files/covers/{videoId}/{filename}）可直接由浏览器访问
 *
 * Phase D-1 P3-6: 注册 {@link RequireRoleInterceptor}，启用 {@code @RequireRole}
 * 自定义权限注解（叠加于 {@code @PreAuthorize}，渐进迁移，零行为变化）。
 *
 * Round 5 P1-11: 注册 {@link FileAccessRateLimitInterceptor}，对
 * {@code /api/files/covers/**} 做 IP 维度限速 + 访问审计，防封面 URL 批量枚举。
 * 依赖（{@link RedisUtil} / {@link FileAccessLogger}）经构造器注入，遵循项目
 * "构造器注入、禁止字段注入" 约定。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RedisUtil redisUtil;
    private final FileAccessLogger fileAccessLogger;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(RedisUtil redisUtil, FileAccessLogger fileAccessLogger,
                        AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.redisUtil = redisUtil;
        this.fileAccessLogger = fileAccessLogger;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations("file:./uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // @RequireRole 运行期校验拦截器；仅对标注了 @RequireRole 的 handler 生效，
        // 其余请求透传（非 HandlerMethod / 无注解直接放行）。
        registry.addInterceptor(new RequireRoleInterceptor())
                .addPathPatterns("/api/**");

        // P2-10: @AuditedLog 审计拦截器 —— 仅对标注了 @AuditedLog 的 handler 异步写
        // operation_logs；其余请求在拦截器内首行透传，零影响、零阻塞。
        // P1-22 修复：传入 ObjectMapper 以安全构建 JSON，避免字符串拼接注入风险
        registry.addInterceptor(new AuditedLogInterceptor(auditLogWriter, objectMapper))
                .addPathPatterns("/api/**");

        // P1-11: 封面 URL 枚举防护 —— 仅对 /api/files/covers/** 限速（60/分钟/IP）+ 访问审计；
        // 其余 /api/files/** 在拦截器内首行透传，零影响。
        registry.addInterceptor(new FileAccessRateLimitInterceptor(redisUtil, fileAccessLogger))
                .addPathPatterns("/api/files/**");
    }
}

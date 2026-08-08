package com.microcourse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.audit.AuditLogWriter;
import com.microcourse.audit.AuditedLogInterceptor;
import com.microcourse.repository.CourseRepository;
import com.microcourse.security.FileAccessLogger;
import com.microcourse.security.FileAccessRateLimitInterceptor;
import com.microcourse.security.RequireRoleInterceptor;
import com.microcourse.util.RedisUtil;
import com.microcourse.web.interceptor.CourseAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 文件资源静态映射（P0-SEC-001：收窄为公开类别白名单，移除通配 /api/files/** 越权映射）。
 *
 * <h3>映射规则</h3>
 * <pre>
 *   公开（permitAll，直接由 WebMvc 静态资源处理器）：
 *     /api/files/covers/**  → file:./uploads/covers/    — 视频封面（{@code &lt;img :src&gt;}）
 *     /api/files/avatars/** → file:./uploads/avatars/   — 用户头像（{@code &lt;el-avatar :src&gt;}）
 *     /api/files/banners/** → file:./uploads/banners/   — 公开轮播图
 *     /api/files/system/**  → file:./uploads/system/    — 平台 Logo / 系统资源（前瞻白名单）
 *     /api/files/videos/**  → file:${video.storage-base-dir}/ — 视频文件（HTML5 {@code &lt;video&gt;} 无 Auth 载体）
 *
 *   私有（需经 {@link com.microcourse.controller.FileAccessController} 对象级授权）：
 *     /api/files/slides/**  — 课件文件（需课程 Owner / 选课校验）
 *     /api/files/storage/** — 申报图片（需提案 Owner 校验）
 *     其余未显式映射的 /api/files/** 路径 → 404（无 Controller、无静态目录）
 * </pre>
 *
 * <h3>安全理由</h3>
 * 旧配置使用通配 {@code /api/files/** → file:./uploads/}，使 {@code uploads/} 下所有文件
 * 均可通过静态资源处理器直接访问，绕过了 Controller 层的 {@code @PreAuthorize} 与对象级
 * Owner 校验。私有文件（slides、storage）可被任意认证用户通过 URL 遍历下载，构成越权漏洞。
 * 修复后每个公开类别显式注册，私有类别由 {@link com.microcourse.controller.FileAccessController}
 * 统一处理，实现静态资源路径与 Controller 授权路径分离的纵深防御。
 *
 * <h3>限速与审计</h3>
 * Round 5 P1-11: {@link FileAccessRateLimitInterceptor} 仅对
 * {@code /api/files/covers/**} 做 IP 维度限速 + 访问审计，防封面 URL 批量枚举。
 * 依赖（{@link RedisUtil} / {@link FileAccessLogger}）经构造器注入。
 *
 * <h3>Phase D-1 P3-6</h3>
 * 注册 {@link RequireRoleInterceptor}，启用 {@code @RequireRole} 自定义权限注解
 * （叠加于 {@code @PreAuthorize}，渐进迁移，零行为变化）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RedisUtil redisUtil;
    private final FileAccessLogger fileAccessLogger;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;
    private final CourseRepository courseRepository;
    /**
     * P1-C 修复(2026-08-03): 与上传/转码共用同一配置源。
     * 此前硬编码 ./uploads/videos/，一旦通过 VIDEO_STORAGE_BASE_DIR 覆写存储目录
     * （CI / 容器 / 运维调整），mp4 直链静态映射与落盘目录漂移 → <video> 404。
     */
    @org.springframework.beans.factory.annotation.Value("${video.storage-base-dir:uploads/videos}")
    private String videoStorageBaseDir;

    public WebMvcConfig(RedisUtil redisUtil, FileAccessLogger fileAccessLogger,
                        AuditLogWriter auditLogWriter, ObjectMapper objectMapper,
                        CourseRepository courseRepository) {
        this.redisUtil = redisUtil;
        this.fileAccessLogger = fileAccessLogger;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 公开类别白名单：仅以下 5 类通过 WebMvc 静态资源映射直接对外暴露。
        // 私有类别（slides、storage 等）经 FileAccessController 对象级授权后访问。
        registry.addResourceHandler("/api/files/covers/**")
                .addResourceLocations("file:./uploads/covers/");
        registry.addResourceHandler("/api/files/avatars/**")
                .addResourceLocations("file:./uploads/avatars/");
        registry.addResourceHandler("/api/files/banners/**")
                .addResourceLocations("file:./uploads/banners/");
        registry.addResourceHandler("/api/files/system/**")
                .addResourceLocations("file:./uploads/system/");
        registry.addResourceHandler("/api/files/videos/**")
                .addResourceLocations("file:" + videoStorageBaseDir + "/");
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

        // Phase 9 P0-1/P0-2 IDOR 修复: 课件写操作对象级授权（纵深防御）。
        // 仅对 /ppt/** 与 /html/** 的写方法（POST/PUT/DELETE/PATCH）校验课程 owner；
        // 读方法放行由 Controller 内显式校验（getUnit 等）。slides/** 与 courseware/**
        // 已分别由 SlideController.verifyAccess / CoursewareQueryService.verifyCourseAccess
        // 保护（含 STUDENT 已选课 / ACADEMIC 通行语义），此处不重复拦截避免破坏读侧。
        registry.addInterceptor(new CourseAccessInterceptor(courseRepository))
                .addPathPatterns("/api/courses/*/ppt/**", "/api/courses/*/html/**");
    }
}

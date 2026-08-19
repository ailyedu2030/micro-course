package com.microcourse.config;

import com.microcourse.service.GrayReleaseService;
import com.microcourse.util.SecurityUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 灰度发布 HTTP 请求过滤器 (F10-D2)
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>从 Spring Security 上下文获取当前登录用户 ID
 *   <li>查询 {@link GrayReleaseService#isGrayUser(Long)}
 *   <li>将结果写入 Request Attribute:
 *       <ul>
 *         <li>{@code gray.isGrayUser} = true/false（默认 false）
 *         <li>{@code gray.userId} = 用户 ID（便于日志关联）
 *       </ul>
 *   <li>后续业务代码 / Service 可读取这些 attribute 决定是否启用灰度逻辑
 * </ol>
 *
 * <h3>【现象】</h3>
 * 原 {@code gray-release.sh} add/roll-out 写入 Redis 但 {@code micro-course-api} 不读取，
 * 灰度白名单实际不改变用户行为（F10-D2 P2 登记）。
 *
 * <h3>【根因】</h3>
 * 后端无灰度上下文注入，业务代码无法知道当前请求是否来自灰度用户。
 *
 * <h3>【修复】</h3>
 * <ol>
 *   <li>{@link GrayReleaseService} 读取 Redis 并缓存 5s（避免每个请求打 Redis）
 *   <li>本过滤器在请求入口注入 {@code gray.*} attributes
 *   <li>业务代码通过 {@code SecurityUtil.isGrayUser()} 或直接读 attribute 决策
 * </ol>
 *
 * <h3>【位置】</h3>
 * {@code @Order(40)} — 在 JWT 认证过滤器（{@code @Order(30)}）之后，
 * 业务 Controller 之前。
 *
 * @author F10-D2 Phase 9 (2026-08-18)
 */
@Component
@Order(40)
public class GrayReleaseFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseFilter.class);

    /** Request Attribute: 当前请求是否来自灰度白名单用户 */
    public static final String ATTR_IS_GRAY_USER = "gray.isGrayUser";
    /** Request Attribute: 当前用户 ID（便于日志关联） */
    public static final String ATTR_USER_ID = "gray.userId";

    private final GrayReleaseService grayReleaseService;

    public GrayReleaseFilter(GrayReleaseService grayReleaseService) {
        this.grayReleaseService = grayReleaseService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Long userId = SecurityUtil.getCurrentUserIdOpt();
            if (userId != null) {
                boolean isGrayUser = grayReleaseService.isGrayUser(userId);
                request.setAttribute(ATTR_IS_GRAY_USER, isGrayUser);
                request.setAttribute(ATTR_USER_ID, userId);
                if (isGrayUser && log.isDebugEnabled()) {
                    log.debug("[GrayRelease] 用户 {} 命中灰度白名单", userId);
                }
            } else {
                // 未登录请求: 不标记为灰度
                request.setAttribute(ATTR_IS_GRAY_USER, Boolean.FALSE);
            }
        } catch (Exception e) {
            // fail-closed: 任何异常 → 默认非灰度
            log.warn("[GrayRelease] 灰度查询失败, 默认非灰度", e);
            request.setAttribute(ATTR_IS_GRAY_USER, Boolean.FALSE);
        }
        filterChain.doFilter(request, response);
    }
}
package com.microcourse.util;

import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类：提供当前用户 ID 和角色判断
 *
 * 依据：
 * - Phase 12 IDOR 防护规范
 * - JWT token 在 JwtAuthenticationFilter 中已写入 SecurityContext
 */
public final class SecurityUtil {

    private static final Logger log = LoggerFactory.getLogger(SecurityUtil.class);

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户 (仅含 ID, 供状态机自审批检查使用)
     */
    public static User getCurrentUser() {
        User user = new User();
        user.setId(getCurrentUserId());
        return user;
    }

    /**
     * 获取当前登录用户 ID
     * <p>类型安全：兼容 Long / String / Number 三种 principal 类型。
     * 解决 Controller 和 Service 中重复定义的问题（P1-I-1 全量修复）。
     *
     * @return 当前用户 ID
     * @throws BusinessException TOKEN_INVALID 当未登录或 token 无效时
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number num) {
            return num.longValue();
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        throw new BusinessException(ErrorCode.TOKEN_INVALID);
    }

    /**
     * 判断当前用户是否为 ADMIN 角色
     *
     * @return true 表示拥有 ADMIN 权限
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前用户是否为指定用户 ID 或 ADMIN
     *
     * @param userId 要校验的用户 ID
     * @return true 表示当前用户是 ADMIN 或 userId 匹配
     */
    /**
     * 判断当前用户是否拥有指定角色
     *
     * @param role 角色名（不含 ROLE_ 前缀，如 "TEACHER"）
     * @return true 表示拥有该角色
     */
    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String authority = "ROLE_" + role;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOwnerOrAdmin(Long userId) {
        if (isAdmin()) {
            return true;
        }
        try {
            return getCurrentUserId().equals(userId);
        } catch (BusinessException e) {
            return false;
        }
    }

    public static boolean isAdminOrAcademic() {
        return isAdmin() || hasRole("ACADEMIC");
    }

    /**
     * 统一自审批阻断校验
     * <p>当审核人(reviewerId)与资源所有者(ownerId)相同时抛出 {@link BusinessException}
     * 使用 {@link ErrorCode#CANNOT_APPROVE_SELF}。
     *
     * @param reviewerId 审核人用户 ID（当前登录用户）
     * @param ownerId    资源所有者用户 ID（申报人/课程教师/负责人）
     * @param message    自定义错误消息
     * @throws BusinessException 当 reviewerId 等于 ownerId 时
     */
    public static void assertNotSelf(Long reviewerId, Long ownerId, String message) {
        if (reviewerId == null || ownerId == null) {
            return; // 缺少比对信息时跳过（保持向后兼容）
        }
        if (reviewerId.equals(ownerId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPROVE_SELF, message);
        }
    }

    /**
     * 获取当前用户 ID，异常时返回 null 而非抛异常
     *
     * @return 当前用户 ID 或 null
     */
    public static Long getCurrentUserIdOpt() {
        try {
            return getCurrentUserId();
        } catch (Exception e) {
            log.warn("获取用户ID失败: {}", e.getMessage());
            return null;
        }
    }
}
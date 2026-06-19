package com.microcourse.util;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
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

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户 ID
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
}
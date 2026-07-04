package com.microcourse.service;

import com.microcourse.dto.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证查询服务接口 — 认证模块的只读/查询操作。
 * 从 AuthServiceImpl 提取，降低单文件复杂度。
 */
public interface AuthQueryService {

    /**
     * 获取当前登录用户信息
     */
    UserVO getCurrentUser();

    /**
     * 获取当前登录用户 ID
     */
    Long getCurrentUserId();

    /**
     * 获取登录失败次数（Redis 优先，本地缓存兜底）
     */
    int getLoginFailureCount(String key);

    /**
     * 递增登录失败次数（静默失败，不抛异常）
     */
    void incrLoginFailureQuietly(String key);

    /**
     * 清除登录失败次数（静默失败，Redis 不可用时清除本地缓存）
     */
    void clearLoginFailureQuietly(String key);

    /**
     * 重置登录锁（测试支持）
     */
    void resetLoginLockout();

    /**
     * 将 User 实体转换为 UserVO
     */
    UserVO convertToUserVO(com.microcourse.entity.User user);

    /**
     * 验证图片魔数（JPEG/PNG/WebP）
     */
    void validateImageMagic(MultipartFile file);
}

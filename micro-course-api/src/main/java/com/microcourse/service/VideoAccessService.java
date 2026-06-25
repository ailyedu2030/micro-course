package com.microcourse.service;

/**
 * 视频访问权限服务接口。
 *
 * <p>判定用户是否允许访问指定课程的视频。调用方 Controller 根据返回的
 * {@link AccessResult} 决定 HTTP 语义（放行 / 403 NOT_ENROLLED）。</p>
 *
 * <p>接口定义遵循"面向接口编程"原则：Controller 注入此接口，不依赖具体实现类。</p>
 */
public interface VideoAccessService {

    /**
     * 检查当前用户是否可访问指定课程的视频。
     *
     * @param userId   当前登录用户 ID（学生选课校验使用）
     * @param courseId 视频所属课程 ID
     * @return {@link AccessResult}，包含允许/拒绝及原因
     */
    AccessResult checkVideoAccess(Long userId, Long courseId);

    /**
     * 判定用户对课程是否持有有效选课记录。
     *
     * <p>有效状态：ENROLLED / APPROVED / COMPLETED。</p>
     *
     * @param userId   用户 ID
     * @param courseId 课程 ID
     * @return true 表示存在有效选课记录
     */
    boolean isEnrolled(Long userId, Long courseId);

    /**
     * 访问判定结果（不可变值对象）。
     */
    final class AccessResult {
        public final boolean allowed;
        public final String reason;

        private AccessResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public static AccessResult allowed(String reason) {
            return new AccessResult(true, reason);
        }

        public static AccessResult denied(String reason) {
            return new AccessResult(false, reason);
        }
    }
}

package com.microcourse.service;

import com.microcourse.entity.User;
import com.microcourse.enums.UserStatus;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 用户状态机 · 唯二入口
 *
 * <p>所有用户状态变更 MUST 通过此接口, 不得直接调用 userRepository.update 修改 status 字段。
 * 这消除了"守卫碎片化"模式 2 的产生条件。</p>
 *
 * <p>业务守卫 hook: 每个 (源状态, 目标状态) 对可注册多个守卫函数,
 * 守卫在状态变更前执行, 任一守卫返回错误列表非空则阻断变更。</p>
 *
 * <p>复用模式: 与 CourseStateMachine 完全一致的设计, 消除跨域不一致</p>
 */
public interface UserStatusStateMachine {

    /**
     * 执行状态变更 (含全部守卫检查 + 乐观锁 + 副作用)
     *
     * @param userId       用户 ID
     * @param targetStatus 目标状态
     * @param actor        当前操作用户 (用于激活守卫等)
     * @param context      业务上下文 (adminForceActivate 等)
     * @return 更新后的 User 实体
     */
    User transition(Long userId, UserStatus targetStatus, User actor, TransitionContext context);

    /**
     * 检查状态变更是否允许 (不修改数据库)
     */
    TransitionGuardResult checkTransition(Long userId, UserStatus targetStatus,
                                          User actor, TransitionContext context);

    /**
     * 注册自定义业务守卫 (按状态对)
     */
    void registerGuard(UserStatus from, UserStatus to,
                       BiFunction<User, TransitionContext, List<String>> guard);

    /**
     * 业务上下文
     */
    class TransitionContext {
        private Boolean adminForceActivate = false;
        private final java.util.Map<String, Object> extras = new java.util.HashMap<>();

        public static TransitionContext empty() {
            return new TransitionContext();
        }

        public static TransitionContext adminForce() {
            TransitionContext ctx = new TransitionContext();
            ctx.adminForceActivate = true;
            return ctx;
        }

        public Boolean getAdminForceActivate() { return adminForceActivate; }
        public void setAdminForceActivate(Boolean adminForceActivate) { this.adminForceActivate = adminForceActivate; }
        public java.util.Map<String, Object> getExtras() { return extras; }
        public void putExtra(String key, Object value) { extras.put(key, value); }
    }

    /**
     * 守卫检查结果
     */
    enum TransitionGuardResult {
        ALLOWED,
        BLOCKED_BY_GUARD,        // 业务守卫阻断
        INVALID_TRANSITION,      // canTransitionTo 不允许
        VERSION_CONFLICT,        // 乐观锁
        COURSE_NOT_FOUND,        // 课程不存在 (此处为 USER_NOT_FOUND)
        NO_PERMISSION            // 角色权限不足
    }
}
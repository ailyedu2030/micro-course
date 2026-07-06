package com.microcourse.service;

import com.microcourse.entity.Course;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 课程状态机 · 唯二入口
 *
 * <p>所有课程状态变更 MUST 通过此接口, 不得直接调用 courseRepository.update 修改 status 字段。
 * 这消除了"守卫碎片化"模式 2 的产生条件。</p>
 *
 * <p>业务守卫 hook: 每个 (源状态, 目标状态) 对可注册多个守卫函数,
 * 守卫在状态变更前执行, 任一守卫返回错误列表非空则阻断变更。</p>
 */
public interface CourseStateMachine {

    /**
     * 执行状态变更 (含全部守卫检查 + 乐观锁 + 副作用)
     *
     * @param courseId     课程 ID
     * @param targetStatus 目标状态
     * @param actor        当前操作用户 (用于自审批阻断)
     * @param context      业务上下文 (如驳回原因、是否强制覆盖)
     * @return 更新后的 Course 实体
     */
    Course transition(Long courseId, CourseStatus targetStatus, User actor, TransitionContext context);

    /**
     * 检查状态变更是否允许 (不修改数据库)
     */
    TransitionGuardResult checkTransition(Long courseId, CourseStatus targetStatus,
                                          User actor, TransitionContext context);

    /**
     * 注册自定义业务守卫 (按状态对)
     *
     * @param from  源状态
     * @param to    目标状态
     * @param guard 守卫函数: 输入 (Course, TransitionContext), 返回错误列表
     */
    void registerGuard(CourseStatus from, CourseStatus to,
                       BiFunction<Course, TransitionContext, List<String>> guard);

    /**
     * 业务上下文
     */
    class TransitionContext {
        private String rejectReason;
        private Boolean forceOverride;
        private final Map<String, Object> extras = new java.util.HashMap<>();

        public static TransitionContext empty() {
            return new TransitionContext();
        }

        public static TransitionContext ofReject(String reason) {
            TransitionContext ctx = new TransitionContext();
            ctx.rejectReason = reason;
            return ctx;
        }

        public String getRejectReason() { return rejectReason; }
        public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
        public Boolean getForceOverride() { return forceOverride; }
        public void setForceOverride(Boolean forceOverride) { this.forceOverride = forceOverride; }
        public Map<String, Object> getExtras() { return extras; }
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
        SELF_APPROVAL_BLOCKED,   // 自审批阻断
        COURSE_NOT_FOUND,        // 课程不存在
        NO_PERMISSION            // 角色权限不足
    }
}
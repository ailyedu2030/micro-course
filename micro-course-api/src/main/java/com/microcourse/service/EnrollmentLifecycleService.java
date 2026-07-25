package com.microcourse.service;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;

/**
 * 选课生命周期写操作接口 — 状态机变更、审计轨迹、通知等。
 * 从 EnrollmentService 拆分以减少 EnrollmentServiceImpl 文件体积（P1-I 代码质量收口）。
 *
 * 包含 4 个核心写操作方法：选课、更新、退课、候补晋升。
 */
public interface EnrollmentLifecycleService {

    /**
     * 执行选课（含行级锁、幂等性、容量检查、候补等完整流程）。
     * 被 EnrollmentService.enroll() 内部调用（metrics 计时包裹层在调用方）。
     */
    EnrollmentVO doEnroll(EnrollmentCreateRequest request);

    /**
     * 更新选课记录（进度、成绩、状态、完成等），含状态机白名单校验和审计历史。
     */
    EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request);

    /**
     * 取消选课/退课，含权限校验、进度检查、通知、自动退款、候补晋升。
     */
    void cancelEnrollment(Long id, Long currentUserId);

    /**
     * P1-I-6: 候补自动晋升 — 当有学生退课腾出名额时，自动将候补队列中最早的一个
     * 学生从 WAITLIST 转为 APPROVED。
     */
    void promoteFirstWaitlistToEnrolled(Long courseId);
}

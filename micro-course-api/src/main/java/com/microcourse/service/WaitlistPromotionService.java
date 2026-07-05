package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.EnrollmentHistory;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentHistoryRepository;
import com.microcourse.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * P1-I-6 重构（最终版）：候补晋升独立 Service。
 *
 * 关键设计决策 — **无锁 CAS 模式**：
 * - 原实现 selectByIdForUpdate 在 cancel 事务提交前调用，因 Hikari 连接共享
 *   + 嵌套事务传播不可靠，导致 PG 行锁等待 30s → CI 卡死
 * - 现改为"原子 INCREMENT_IF_NOT_FULL"无锁两阶段：
 *   1. 找 WAITLIST 队列第一个
 *   2. atomicIncrementIfNotFull 尝试占位（原子 SQL，超容量返回 0）
 *   3. 如果成功：CAS 升级 enrollment 状态为 APPROVED
 *   4. 如果 CAS 失败（并发晋升）：rollback INC（atomicDecrementStudentCount）
 *   5. 如果 INCREMENT 失败（容量已满）：直接退出
 *
 * 这种方式不依赖行锁，CAS 通过 SQL 自身保证。dec/inc 是配对操作，
 * 仅在 CAS 成功时净效果为 0（取消一个人 + 晋升一个人）。
 */
@Service
public class WaitlistPromotionService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionService.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final NotificationService notificationService;

    public WaitlistPromotionService(CourseRepository courseRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   EnrollmentHistoryRepository enrollmentHistoryRepository,
                                   NotificationService notificationService) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentHistoryRepository = enrollmentHistoryRepository;
        this.notificationService = notificationService;
    }

    /**
     * 晋升候补队列第一名 → APPROVED（无锁 CAS）。
     * 由 {@code EnrollmentServiceImpl.cancelEnrollment} 的 afterCommit 钩子触发，
     * 此时 cancel 事务已提交（student_count 已 -1），promote 只需把名额
     * 转给候补学生即可（净效果 = 0）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class, timeout = 15)
    public void promoteFirstWaitlistToEnrolled(Long courseId) {
        if (courseId == null) return;

        try {
            doPromote(courseId);
        } catch (Exception e) {
            log.error("[WaitlistPromotion] 候补晋升异常 courseId={}（不影响主流程）", courseId, e);
            // 吞掉异常：候补晋升失败不应影响 cancel 主流程
            // 但事务需要回滚（如果已经 INC 但 CAS 失败），让 @Transactional 自动处理
        }
    }

    private void doPromote(Long courseId) {
        // 阶段 1: 找候补队列最早一个 WAITLIST
        Enrollment next = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .orderByAsc(Enrollment::getEnrolledAt)
                        .last("LIMIT 1"));
        if (next == null) {
            log.debug("[WaitlistPromotion] 课程 {} 无候补", courseId);
            return;  // 无候补，不动 student_count（cancel 已 -1）
        }

        // 阶段 2: 原子 INCREMENT_IF_NOT_FULL 占位
        // cancel 事务已 DECREMENT，promote 只需把空位给候补学生（净效果 0）
        int inc = courseRepository.atomicIncrementIfNotFull(courseId);
        if (inc == 0) {
            log.debug("[WaitlistPromotion] 课程 {} 容量已满，跳过晋升", courseId);
            return;  // 容量满，不动 student_count
        }

        // 阶段 3: CAS 升级 enrollment 为 APPROVED
        int updated = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, next.getId())
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.APPROVED.getValue())
                        .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            // CAS 失败（已被并发晋升），回滚 INC
            courseRepository.atomicDecrementStudentCount(courseId);
            log.debug("[WaitlistPromotion] 候补 {} CAS 失败，回滚 INC", next.getId());
            return;
        }

        // 阶段 4: 写历史
        EnrollmentHistory history = new EnrollmentHistory();
        history.setEnrollmentId(next.getId());
        history.setPreviousStatus(EnrollmentStatus.WAITLIST.getValue());
        history.setNewStatus(EnrollmentStatus.APPROVED.getValue());
        history.setReason("WAITLIST_PROMOTE");
        history.setOperatorId(null);
        history.setCreatedAt(LocalDateTime.now());
        enrollmentHistoryRepository.insert(history);

        // 阶段 5: 异步通知
        try {
            notificationService.notifyAsync(next.getUserId(),
                    NotificationType.ENROLLMENT_SUCCESS,
                    "候补录取通知",
                    "您已从候补队列中被录取，可以开始学习",
                    courseId);
        } catch (Exception e) {
            log.warn("[WaitlistPromotion] 候补通知发送失败 id={}", next.getId(), e);
        }
        log.info("[WaitlistPromotion] 候补晋升成功 enrollmentId={} courseId={}", next.getId(), courseId);
    }
}
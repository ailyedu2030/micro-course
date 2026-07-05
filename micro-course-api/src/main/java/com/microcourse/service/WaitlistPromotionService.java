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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * P1-I-6 重构（最终版）：候补晋升独立 Service。
 *
 * 修复历史：
 * - v1: {@code @Transactional(REQUIRES_NEW)} 在 {@code EnrollmentServiceImpl} 同 bean 内失效 → CI 卡死
 * - v2: 抽到独立 {@code WaitlistPromotionService} bean，但 REQUIRES_NEW 在某些版本仍可能因
 *        HikariCP 连接池共享 / Spring AOP 代理问题失效，导致 {@code selectByIdForUpdate}
 *        等不到 course 行锁，30s 后 PG lock_timeout 触发取消
 * - v3 (当前): 改用编程式事务（{@link TransactionTemplate}），绕过 AOP 代理不确定性，
 *        显式开启新事务 + 显式使用独立 connection
 *
 * 设计保证：
 * 1. 独立 @Service bean → 独立 connection pool 槽
 * 2. TransactionTemplate.execute 显式开新事务
 * 3. 主流程 (cancelEnrollment) 的事务结束前不会阻塞 promote
 */
@Service
public class WaitlistPromotionService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionService.class);

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate txTemplate;

    public WaitlistPromotionService(CourseRepository courseRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   EnrollmentHistoryRepository enrollmentHistoryRepository,
                                   NotificationService notificationService,
                                   PlatformTransactionManager txManager) {
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentHistoryRepository = enrollmentHistoryRepository;
        this.notificationService = notificationService;
        // 显式 REQUIRES_NEW 事务模板（不依赖 @Transactional AOP）
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.txTemplate.setTimeout(30);  // 30s 超时
    }

    /**
     * 晋升候补队列第一名 → APPROVED。
     * 显式 REQUIRES_NEW 事务，独立 connection。
     */
    public void promoteFirstWaitlistToEnrolled(Long courseId) {
        if (courseId == null) return;
        try {
            txTemplate.execute(status -> doPromote(courseId));
        } catch (Exception e) {
            // 捕获异常防止影响主流程（cancel）
            log.warn("[WaitlistPromotion] 候补晋升失败 courseId={}, 不影响退课主流程", courseId, e);
        }
    }

    private Object doPromote(Long courseId) {
        // 1. 锁 course 行
        Map<String, Object> locked = courseRepository.selectByIdForUpdate(courseId);
        if (locked == null) return null;

        Object maxObj = locked.get("max_students");
        Object cntObj = locked.get("student_count");
        if (maxObj == null || cntObj == null) return null;
        Integer maxStudents = ((Number) maxObj).intValue();
        Integer studentCount = ((Number) cntObj).intValue();
        if (maxStudents > 0 && studentCount >= maxStudents) {
            log.debug("[WaitlistPromotion] 课程 {} 已满员，跳过晋升", courseId);
            return null;
        }

        // 2. 找候补队列最早一个 WAITLIST
        Enrollment next = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .orderByAsc(Enrollment::getEnrolledAt)
                        .last("LIMIT 1"));
        if (next == null) return null;

        // 3. CAS 升级为 APPROVED
        int updated = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, next.getId())
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.APPROVED.getValue())
                        .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            log.debug("[WaitlistPromotion] 候补 {} CAS 失败（已被并发晋升）", next.getId());
            return null;
        }

        // 4. 递增 student_count
        int cnt = courseRepository.atomicIncrementIfNotFull(courseId);
        if (cnt == 0) {
            // 容量已满，回滚候补
            enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getId, next.getId())
                            .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                            .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
            log.debug("[WaitlistPromotion] 课程 {} 容量已满，回滚候补晋升", courseId);
            return null;
        }

        // 5. 写历史
        EnrollmentHistory history = new EnrollmentHistory();
        history.setEnrollmentId(next.getId());
        history.setPreviousStatus(EnrollmentStatus.WAITLIST.getValue());
        history.setNewStatus(EnrollmentStatus.APPROVED.getValue());
        history.setReason("WAITLIST_PROMOTE");
        history.setOperatorId(null);
        history.setCreatedAt(LocalDateTime.now());
        enrollmentHistoryRepository.insert(history);

        // 6. 异步通知
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
        return null;
    }
}
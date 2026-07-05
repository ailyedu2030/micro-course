package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentQueryRequest;
import com.microcourse.dto.EnrollmentRankingVO;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.StudentDetailVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.EnrollmentHistory;
import com.microcourse.entity.Major;
import com.microcourse.entity.Order;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentHistoryRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.util.SecurityUtil;
import com.microcourse.service.EnrollmentQueryService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.EnrollmentStatsService;
import com.microcourse.service.CertificateService;
import com.microcourse.service.BadgeService;
import com.microcourse.service.NotificationService;
import com.microcourse.service.CourseService;
import com.microcourse.service.OrderService;
import com.microcourse.enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentServiceImpl.class);

    /** P0-04: 防止 OrderServiceImpl.refund() → cancelEnrollment → orderService.refund() 递归循环 */
    private static final ThreadLocal<Boolean> REFUND_REENTRANT = ThreadLocal.withInitial(() -> false);

    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentHistoryRepository enrollmentHistoryRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ClassesRepository classesRepository;
    private final MajorRepository majorRepository;
    private final CertificateService certificateService;
    private final BadgeService badgeService;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final CourseService courseService;
    private final NotificationService notificationService;
    private final EnrollmentStatsService statsService;
    private final EnrollmentQueryService queryService;
    private final com.microcourse.metrics.EnrollmentMetrics metrics;
    private final org.springframework.transaction.support.TransactionTemplate txTemplate;

    public EnrollmentServiceImpl(EnrollmentRepository enrollmentRepository,
                                 EnrollmentHistoryRepository enrollmentHistoryRepository,
                                 CourseRepository courseRepository,
                                 UserRepository userRepository,
                                 ClassesRepository classesRepository,
                                 MajorRepository majorRepository,
                                 CertificateService certificateService,
                                 BadgeService badgeService,
                                 OrderRepository orderRepository,
                                 OrderService orderService,
                                  EnrollmentStatsService statsService,
                                  EnrollmentQueryService queryService,
                                  CourseService courseService,
                                  NotificationService notificationService,
                                  com.microcourse.metrics.EnrollmentMetrics metrics,
                                  org.springframework.transaction.PlatformTransactionManager txManager) {
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentHistoryRepository = enrollmentHistoryRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.certificateService = certificateService;
        this.badgeService = badgeService;
        this.classesRepository = classesRepository;
        this.majorRepository = majorRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.courseService = courseService;
        this.notificationService = notificationService;
        this.statsService = statsService;
        this.queryService = queryService;
        this.metrics = metrics;
        this.txTemplate = new org.springframework.transaction.support.TransactionTemplate(txManager);
        // ★ 客户体验修复 v1.7.0: 软删旧 CANCELLED enrollment 必须走 REQUIRES_NEW,
        // 否则默认 REQUIRED 会加入主 enroll() 事务, 主事务异常回滚时撤销删除
        this.txTemplate.setPropagationBehavior(
            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.txTemplate.setName("reenroll-soft-delete-tx");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO enroll(EnrollmentCreateRequest request) {
        // ★ 业务逻辑审计 P0-2 增强：可观测性 — Timer 记录完整耗时（含行级锁）
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start();
        boolean success = false;
        try {
            EnrollmentVO result = doEnroll(request);
            success = true;
            return result;
        } finally {
            sample.stop(metrics.enrollTimer());
            if (success) {
                metrics.recordSuccess();
            } else {
                metrics.recordError();
            }
        }
    }

    private EnrollmentVO doEnroll(EnrollmentCreateRequest request) {
        // ★ 业务逻辑审计 P0-3 增强：功能开关（紧急回滚）
        if (!com.microcourse.config.EnrollmentFeatureFlag.isDynamicallyEnabled()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "选课服务暂时不可用，请稍后重试");
        }
        // 幂等性优先检查：已选过则直接返回
        LambdaQueryWrapper<Enrollment> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Enrollment::getUserId, request.getUserId())
                .eq(Enrollment::getCourseId, request.getCourseId());
        Enrollment existingEnrollment = enrollmentRepository.selectOne(existingWrapper);
        if (existingEnrollment != null) {
            // 客户体验修复 v1.7.0: 退课后重新选课
            // 问题: UNIQUE(user_id, course_id) WHERE deleted_at IS NULL 约束 + 软删的 CANCELLED 记录阻挡新 ENROLLED
            // 解决: 用 @TableLogic 软删除 (deleted_at = NOW()) 旧 CANCELLED 记录
            //      partial unique index 释放后, 走正常 enroll 流程
            // 状态机: CANCELLED 是终态, 不能状态转换, 所以不能 updateById. 改用 deleteById 触发 @TableLogic
            if (EnrollmentStatus.CANCELLED.getValue().equals(existingEnrollment.getEnrollmentStatus())) {
                LOG.info("退课后重新选课: userId={}, courseId={}, 软删旧 enrollmentId={}",
                    request.getUserId(), request.getCourseId(), existingEnrollment.getId());
                // 检查课程是否仍可选
                java.util.Map<String, Object> lockedCourseCheck = courseRepository.selectByIdForUpdate(request.getCourseId());
                if (lockedCourseCheck == null) {
                    throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
                }
                Integer checkStatus = (Integer) lockedCourseCheck.get("status");
                Object checkDeletedAt = lockedCourseCheck.get("deleted_at");
                if (checkDeletedAt != null) {
                    throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程已删除");
                }
                if (checkStatus == null || !CourseStatus.fromCode(checkStatus).isSelectable()) {
                    throw new BusinessException(ErrorCode.COURSE_NOT_PUBLISHED,
                        "课程" + describeCourseStatus(checkStatus) + "，无法重新选课");
                }
                // P1-25: 退课物理删除改用软更新 — 复用旧记录，避免 REQUIRES_NEW 事务影响主事务
                enrollmentRepository.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, existingEnrollment.getId())
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue())
                        .set(Enrollment::getEnrollmentStatus, "REENROLLING")
                        .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
                LOG.info("退课后重新选课-软更新旧 enrollment 为 REENROLLING: enrollmentId={}", existingEnrollment.getId());
                // 注意: cancelEnrollment 已 atomicDecrementStudentCount, 这里不要再 +1
                // 走下面的正常 enroll 流程会自动 +1
                // P1-25: 旧 CANCELLED enrollment 已软更新为 REENROLLING，不再物理删除
                // 不 return, 继续走下面的正常 enroll 流程
            } else {
                // 其他状态 (APPROVED/ENROLLED/IN_PROGRESS/COMPLETED/WAITLIST) 幂等返回
                return convertToVO(existingEnrollment);
            }
        }

        // ★ 业务逻辑审计 P0-1 压测发现追加：行级锁 (SELECT ... FOR UPDATE)
        // 之前的实现虽然用了"原子 SQL"，但 INSERT...SELECT...WHERE c.student_count < c.max_students
        // 仍然不原子——多个并发事务能看到相同的 student_count 旧值。
        // 压测结果 (50 并发对 max=5): 修复前 11/15 超卖; 加行级锁后 5/15 满员。
        // 必须在事务内锁课程行，后续所有 SQL 都基于锁后的一致快照。
        java.util.Map<String, Object> lockedCourse = courseRepository.selectByIdForUpdate(request.getCourseId());
        if (lockedCourse == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        Integer lockedStatus = (Integer) lockedCourse.get("status");
        Integer maxStudents = (Integer) lockedCourse.get("max_students");
        Integer studentCount = ((Number) lockedCourse.get("student_count")).intValue();
        // P0 修复 v1.7.0: deleted_at 是 timestamp 列,不能直接 cast String;判断 null 即可
        // 软删除的课程在 SELECT 时已经被 deleted_at IS NULL 过滤掉,这里只是兜底二次校验
        Object deletedAtRaw = lockedCourse.get("deleted_at");
        boolean isDeleted = deletedAtRaw != null;

        if (isDeleted) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程已删除");
        }
        if (lockedStatus == null || !CourseStatus.fromCode(lockedStatus).isSelectable()) {
            throw new BusinessException(ErrorCode.COURSE_NOT_PUBLISHED,
                "课程" + describeCourseStatus(lockedStatus) + "，无法选课");
        }

        // SECURITY: 付费课程必须通过订单支付,不能直接选课
        // v1.7.0 P0 修复: sourceChannel='PAYMENT' 表示这是订单支付后的自动选课,跳过付费检查
        // 旧逻辑会把"支付后自动选课"也卡住,造成订单支付 500 (死锁: pay→enroll→reject)
        Course course = courseRepository.selectById(request.getCourseId());  // 拿到完整 entity
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        // P0-06 修复：若 sourceChannel 为 PAYMENT，验证用户有该课程的 PAID 订单
        if ("PAYMENT".equals(request.getSourceChannel())) {
            // 【P1-05 修复】pay() 调用 autoEnroll() 时订单仍为 PENDING（先选课再标记支付），
            // 因此同时检查 PENDING 和 PAID 订单，避免"非法支付来源"误拦截
            Long paidCount = orderRepository.selectCount(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, request.getUserId())
                    .eq(Order::getCourseId, request.getCourseId())
                    .in(Order::getStatus, "PENDING", "PAID"));
            if (paidCount == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法支付来源");
            }
        }
        if (!"PAYMENT".equals(request.getSourceChannel())) {
            com.microcourse.dto.CoursePricingInfoVO pricing = courseService.getMyPricing(request.getCourseId());
            boolean studentShouldPay = pricing != null
                    && !pricing.isFree()
                    && pricing.getFinalPrice() != null
                    && pricing.getFinalPrice().compareTo(java.math.BigDecimal.ZERO) > 0;
            if (studentShouldPay) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程为付费课程，请先购买");
            }
        }
        // Check user exists
        User user = userRepository.selectById(request.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // ★ 业务逻辑审计 P0-1 修复：原子选课（行级锁 + 容量检查 + 插入 + 增计数）
        // 因为课程行已锁定，INSERT...SELECT 的 student_count < max_students 检查是准确的
        int inserted = enrollmentRepository.atomicInsertIfCapacity(
                request.getUserId(),
                request.getCourseId(),
                EnrollmentStatus.APPROVED.getValue(),
                request.getSourceChannel());

        if (inserted == 0) {
            // 区分"已选过"vs"课程满员"vs"课程不可选"
            Enrollment existing = enrollmentRepository.selectOne(new LambdaQueryWrapper<Enrollment>()
                    .eq(Enrollment::getUserId, request.getUserId())
                    .eq(Enrollment::getCourseId, request.getCourseId()));
            if (existing != null) {
                return convertToVO(existing);
            }

            // ★ 业务逻辑审计 P1 修复：课程满员 → 自动入候补队列
            // 使用行级锁后的 studentCount/maxStudents（不是 course.getXxx()，那个是锁前的）
            if (maxStudents != null && maxStudents > 0 && studentCount >= maxStudents) {
                metrics.recordOvercapacityPrevented();  // P0-1 修复生效证据：容量被保护
                int waitlistInserted = enrollmentRepository.atomicInsertIfEnrollable(
                        request.getUserId(),
                        request.getCourseId(),
                        EnrollmentStatus.WAITLIST.getValue(),
                        request.getSourceChannel());
                if (waitlistInserted > 0) {
                    // 计算候补位置
                    int position = enrollmentRepository.countWaitlistByCourseId(request.getCourseId(), EnrollmentStatus.WAITLIST.getValue());
                    Enrollment waitlistEnrollment = enrollmentRepository.selectOne(new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, request.getUserId())
                            .eq(Enrollment::getCourseId, request.getCourseId()));
                    if (waitlistEnrollment != null) {
                        // 通知学生入候补
                        notificationService.notifyAsync(
                                request.getUserId(),
                                NotificationType.ENROLLMENT_WAITLIST,
                                "已进入候补队列",
                                "您已进入《" + course.getTitle() + "》的候补队列，当前位置 #" + position,
                                course.getId());
                        recordHistory(waitlistEnrollment.getId(), null, EnrollmentStatus.WAITLIST, request.getUserId(), "WAITLIST");
                        metrics.recordWaitlist();
                        return convertToVO(waitlistEnrollment);
                    }
                }
            }
            throw new BusinessException(ErrorCode.COURSE_NOT_PUBLISHED,
                "课程" + describeCourseStatus(lockedStatus) + "或已选过，无法选课");
        }

        // 双闸门：原子增计数也带容量检查（防御深度）
        courseRepository.atomicIncrementIfNotFull(request.getCourseId());

        // 查询刚插入的 enrollment
        Enrollment newEnrollment = enrollmentRepository.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getUserId, request.getUserId())
                .eq(Enrollment::getCourseId, request.getCourseId()));
        if (newEnrollment == null) {
            // 极端边缘情况：原子插入成功但 select 失败
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND, "选课记录异常，请联系管理员");
        }

        // P0-2 修复（审计空洞）：选课成功后写入 enrollment_histories 审计轨迹。
        recordHistory(newEnrollment.getId(), null, EnrollmentStatus.APPROVED, request.getUserId(), "ENROLL");

        // Phase B-2 (P0-7)：选课成功后异步通知学生
        notificationService.notifyAsync(
                request.getUserId(),
                NotificationType.ENROLLMENT_SUCCESS,
                "选课成功",
                "您已成功选课《" + course.getTitle() + "》，开始学习吧！",
                course.getId());
        return convertToVO(newEnrollment);
    }

    @Override
    public List<EnrollmentVO> getMyEnrollments(Long userId, Boolean completed) {
        return queryService.getMyEnrollments(userId, completed);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getEnrollmentPage(EnrollmentQueryRequest query) {
        return queryService.getEnrollmentPage(query);
    }

    @Override
    public List<EnrollmentVO> getCourseEnrollments(Long courseId) {
        return queryService.getCourseEnrollments(courseId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EnrollmentVO> getCourseEnrollmentPage(Long courseId, int page, int size) {
        return queryService.getCourseEnrollmentPage(courseId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentRankingVO> getCourseRanking(Long courseId, int limit, Long currentUserId) {
        return queryService.getCourseRanking(courseId, limit, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EnrollmentVO updateEnrollment(Long id, EnrollmentUpdateRequest request) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        // R12 P0-3: 教师仅能修改自己课程学生的选课记录
        if (!SecurityUtil.isAdmin()) {
            Course c = courseRepository.selectById(enrollment.getCourseId());
            if (c != null && !SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }

        if (request.getProgress() != null) {
            enrollment.setProgress(request.getProgress());
        }
        if (request.getCompleted() != null) {
            enrollment.setCompleted(request.getCompleted());
            if (request.getCompleted()) {
                enrollment.setCompletedAt(LocalDateTime.now());
                try {
                    certificateService.issueCertificate(enrollment.getUserId(), enrollment.getCourseId());
                } catch (Exception e) {
                    // DF-006 修复: 证书颁发失败升级为 ERROR 日志(保留 fail-open 不中断完成流程)
                    LOG.error("[Enrollment] 证书自动颁发失败 userId={} courseId={}", enrollment.getUserId(), enrollment.getCourseId(), e);
                }
                try {
                    badgeService.checkAndAwardCourseCompletion(
                            enrollment.getUserId(), enrollment.getCourseId(),
                            enrollmentRepository.selectCount(
                                    new LambdaQueryWrapper<Enrollment>()
                                            .eq(Enrollment::getUserId, enrollment.getUserId())),
                            enrollmentRepository.selectCount(
                                    new LambdaQueryWrapper<Enrollment>()
                                            .eq(Enrollment::getUserId, enrollment.getUserId())
                                            .eq(Enrollment::getCompleted, true)));
                } catch (Exception e) {
                    LOG.error("[Enrollment] 徽章自动颁发失败 userId={} courseId={}", enrollment.getUserId(), enrollment.getCourseId(), e);
                }
            }
        }
        if (request.getFinalScore() != null) {
            enrollment.setFinalScore(request.getFinalScore());
        }
        if (request.getFinalGrade() != null) {
            enrollment.setFinalGrade(request.getFinalGrade());
        }
        if (request.getEnrollmentStatus() != null) {
            // P0-2 修复：状态变更走状态机白名单校验，并写入审计历史
            EnrollmentStatus currentStatus = EnrollmentStatus.fromString(enrollment.getEnrollmentStatus());
            EnrollmentStatus targetStatus = parseTargetStatus(request.getEnrollmentStatus());
            if (currentStatus == null) {
                // 存量空状态记录：视为初始化，允许置为任一合法契约状态
                recordHistory(enrollment.getId(), null, targetStatus, getCurrentUserIdOrNull(), "STATUS_UPDATE");
                enrollment.setEnrollmentStatus(targetStatus.getValue());
            } else if (currentStatus != targetStatus) {
                if (!currentStatus.canTransitionTo(targetStatus)) {
                    throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                            "不允许从 " + currentStatus.getValue() + " 转换到 " + targetStatus.getValue());
                }
                recordHistory(enrollment.getId(), currentStatus, targetStatus, getCurrentUserIdOrNull(), "STATUS_UPDATE");
                enrollment.setEnrollmentStatus(targetStatus.getValue());
            }
            // currentStatus == targetStatus：同状态幂等，无变更、无审计
        }

        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
        return convertToVO(enrollment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelEnrollment(Long id, Long currentUserId) {
        Enrollment enrollment = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getId, id));
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        // IDOR 校验(SEC-NEW-2 修复):优先使用 Controller 传入的 currentUserId,fallback 用 SecurityUtil
        Long effectiveUserId = currentUserId != null ? currentUserId : SecurityUtil.getCurrentUserId();
        boolean isAdmin = SecurityUtil.isAdmin();
        if (!isAdmin && !enrollment.getUserId().equals(effectiveUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P0-2 修复（审计空洞）：取消前记录状态变更轨迹（历史值 ENROLLED 经 fromString 归一为 APPROVED）
        EnrollmentStatus fromStatus = EnrollmentStatus.fromString(enrollment.getEnrollmentStatus());
        // R8 业务逻辑 P0-1: 状态机白名单校验(cancelEnrollment 之前缺了此步)
        if (fromStatus != null && !fromStatus.canTransitionTo(EnrollmentStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "不允许从 " + fromStatus.getValue() + " 转换到 CANCELLED");
        }
        enrollment.setEnrollmentStatus(EnrollmentStatus.CANCELLED.getValue());
        enrollment.setUpdatedAt(LocalDateTime.now());
        enrollmentRepository.updateById(enrollment);
        recordHistory(enrollment.getId(), fromStatus, EnrollmentStatus.CANCELLED, effectiveUserId, "CANCEL");

        // ★ Round 8-4 修复(P0)：原子同步 courses.student_count（仅状态从非取消→取消时 -1）。
        // 重复取消（fromStatus 已是 CANCELLED）幂等不重复扣减；GREATEST 兜底不会出现负数。
        boolean wasEnrolled = (fromStatus != EnrollmentStatus.CANCELLED
                && fromStatus != EnrollmentStatus.WAITLIST);
        if (wasEnrolled && enrollment.getCourseId() != null) {
            courseRepository.atomicDecrementStudentCount(enrollment.getCourseId());
        }

        // ★ 业务逻辑审计 P0-1 新发现：自动晋升候补
        // spec §3.2: "WAITLIST → APPROVED: 有人退课 → 按候补顺序自动录取"
        // 之前漏了！学生退课后,候补中的第一个学生没有自动 ENROLLED
        // 现在：退课 → student_count-1 → 找 WAITLIST 最早一个 → 改 APPROVED
        if (wasEnrolled && enrollment.getCourseId() != null) {
            promoteFirstWaitlistToEnrolled(enrollment.getCourseId());
        }

        // P0-04: 修复退款递归循环 — 由 OrderServiceImpl.refund() 统一编排退款和取消选课，
        // cancelEnrollment 不再重复调用 orderService.refund()
        if (enrollment.getCourseId() != null) {
            LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(Order::getUserId, enrollment.getUserId())
                    .eq(Order::getCourseId, enrollment.getCourseId())
                    .eq(Order::getStatus, "PAID");
            Order paidOrder = orderRepository.selectOne(orderWrapper);
            if (paidOrder != null && !REFUND_REENTRANT.get()) {
                REFUND_REENTRANT.set(true);
                try {
                    LOG.info("[Enrollment] 取消选课 {} 时跳过退款（由 OrderService 统一编排）", enrollment.getId());
                } finally {
                    REFUND_REENTRANT.remove();
                }
            }
        }
    }

    // ---- 统计方法全部委托 EnrollmentStatsService ---- //

    @Override
    @Transactional(readOnly = true)
    public long countByTeacherId(Long teacherId) {
        return statsService.countByTeacherId(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countCompletedByTeacherId(Long teacherId) {
        return statsService.countCompletedByTeacherId(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public double getAvgScoreByTeacherId(Long teacherId) {
        return statsService.getAvgScoreByTeacherId(teacherId);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDetailVO getStudentDetail(Long userId) {
        return queryService.getStudentDetail(userId);
    }

    private EnrollmentVO convertToVO(Enrollment enrollment) {
        EnrollmentVO vo = new EnrollmentVO();
        vo.setId(enrollment.getId());
        vo.setCourseId(enrollment.getCourseId());
        vo.setUserId(enrollment.getUserId());
        vo.setProgress(enrollment.getProgress());
        vo.setCompleted(enrollment.getCompleted());
        vo.setFinalScore(enrollment.getFinalScore());
        vo.setFinalGrade(enrollment.getFinalGrade());
        vo.setEnrollmentStatus(enrollment.getEnrollmentStatus());
        vo.setSourceChannel(enrollment.getSourceChannel());
        vo.setBundleId(enrollment.getBundleId());
        vo.setEnrolledAt(enrollment.getEnrolledAt());
        vo.setCompletedAt(enrollment.getCompletedAt());
        vo.setUpdatedAt(enrollment.getUpdatedAt());

        // Load course info including teacher
        if (enrollment.getCourseId() != null) {
            Course course = courseRepository.selectById(enrollment.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
                vo.setCourseTitle(course.getTitle());
                vo.setCoverUrl(course.getCoverUrl());
                if (course.getTeacherId() != null) {
                    User teacher = userRepository.selectById(course.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    }
                }
            }
        }

        // P0-3: 填充用户维度字段
        if (enrollment.getUserId() != null) {
            User user = userRepository.selectById(enrollment.getUserId());
            if (user != null) {
                vo.setUserName(user.getRealName());
                vo.setUsername(user.getUsername());
                vo.setRealName(user.getRealName());
                if (user.getClassId() != null) {
                    Classes cls = classesRepository.selectById(user.getClassId());
                    if (cls != null) {
                        vo.setClassName(cls.getName());
                    }
                }
                if (user.getMajorId() != null) {
                    Major major = majorRepository.selectById(user.getMajorId());
                    if (major != null) {
                        vo.setMajorName(major.getName());
                    }
                }
            }
        }

        return vo;
    }

    /**
     * P0-2：写入选课状态变更审计历史（enrollment_histories）。
     *
     * @param enrollmentId 选课记录 ID
     * @param fromStatus   变更前状态（可为 null，如初次选课）
     * @param toStatus     变更后状态（非空）
     * @param operatorId   操作人 ID（可为 null，如系统/无认证上下文）
     * @param reason       变更动作/原因（ENROLL / CANCEL / STATUS_UPDATE ...）
     */
    /**
     * ★ 业务逻辑审计 P0-1 新增：候补自动晋升
     * <p>当有学生退课腾出名额时,自动将候补队列中最早的 (FIFO) 一个学生从 WAITLIST 转为 APPROVED。</p>
     * <p>spec §3.2: "WAITLIST → APPROVED: 有人退课 → 按候补顺序自动录取"</p>
     * <p>实现要点:</p>
     * <ol>
     *   <li>行级锁: 重新锁 course 行,避免并发 cancel 造成双重晋升</li>
     *   <li>只晋升一个学生 (不退课一人进课一人,而不是 N 个退课 N 个进)</li>
     *   <li>通知: 给晋升学生发通知</li>
     * </ol>
     */
    private void promoteFirstWaitlistToEnrolled(Long courseId) {
        // 1. 重新锁 course 行 (与 enroll() 一致的事务隔离保证)
        java.util.Map<String, Object> locked = courseRepository.selectByIdForUpdate(courseId);
        if (locked == null) return;
        Integer maxStudents = (Integer) locked.get("max_students");
        Integer studentCount = ((Number) locked.get("student_count")).intValue();
        if (maxStudents == null || maxStudents == 0) return;  // 不限人数 → 不需要晋升
        if (studentCount >= maxStudents) return;  // 还有人占着,先不晋升

        // 2. 找候补队列最早一个 (按 enrolled_at ASC,先来先进)
        Enrollment next = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .orderByAsc(Enrollment::getEnrolledAt)
                        .last("LIMIT 1"));
        if (next == null) return;  // 候补空

        // 3. 状态机校验 (防御深度): WAITLIST → APPROVED 是合法
        EnrollmentStatus fromStatus = EnrollmentStatus.WAITLIST;
        if (!fromStatus.canTransitionTo(EnrollmentStatus.APPROVED)) {
            LOG.error("候补晋升状态机不通过: WAITLIST→APPROVED");
            return;
        }

        // 4. CAS 更新: 状态变更 + 增计数
        int updated = enrollmentRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, next.getId())
                        .eq(Enrollment::getEnrollmentStatus, EnrollmentStatus.WAITLIST.getValue())
                        .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.APPROVED.getValue())
                        .set(Enrollment::getUpdatedAt, LocalDateTime.now()));
        if (updated == 0) {
            LOG.warn("候补晋升 CAS 失败, id={} (并发取消冲突,下次重试)", next.getId());
            return;
        }

        // 5. 增计数 (因晋升,student_count +1)
        courseRepository.atomicIncrementIfNotFull(courseId);

        // 6. 写历史
        recordHistory(next.getId(), fromStatus, EnrollmentStatus.APPROVED, null, "WAITLIST_PROMOTE");

        // 7. 通知晋升学生
        // R8 P1-C-3: 查询课程标题（替代直接拼 hard-coded courseId）
        String courseTitle = null;
        try {
            Course courseForNotify = courseRepository.selectById(courseId);
            if (courseForNotify != null) courseTitle = courseForNotify.getTitle();
        } catch (Exception e) {
            LOG.warn("[notifyNextInQueue] 获取课程标题失败, courseId={}, error={}", courseId, e.getMessage());
        }
        String finalCourseTitle = courseTitle;
        // P0: 仅在事务提交后发送通知，避免事务回滚导致虚假通知
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            notificationService.notifyAsync(
                                    next.getUserId(),
                                    NotificationType.ENROLLMENT_SUCCESS,
                                    "候补录取通知",
                                    "您已从候补队列中被录取《" + (finalCourseTitle != null ? finalCourseTitle : "课程 ID=" + courseId) + "》,可开始学习。",
                                    courseId);
                        } catch (Exception e) {
                            LOG.warn("候补通知发送失败, id={}, userId={}", next.getId(), next.getUserId(), e);
                        }
                    }
                });
        }

        LOG.info("候补晋升完成: courseId={}, userId={}, enrollmentId={}",
                courseId, next.getUserId(), next.getId());
    }

    private void recordHistory(Long enrollmentId, EnrollmentStatus fromStatus,
                               EnrollmentStatus toStatus, Long operatorId, String reason) {
        EnrollmentHistory history = new EnrollmentHistory();
        history.setEnrollmentId(enrollmentId);
        history.setPreviousStatus(fromStatus != null ? fromStatus.getValue() : null);
        history.setNewStatus(toStatus.getValue());
        history.setReason(reason);
        history.setOperatorId(operatorId);
        history.setCreatedAt(LocalDateTime.now());
        enrollmentHistoryRepository.insert(history);
    }

    /** 解析目标状态字符串为契约枚举；非法值转为 400 业务异常而非 500。 */
    private EnrollmentStatus parseTargetStatus(String status) {
        try {
            return EnrollmentStatus.fromString(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "未知的选课状态: " + status);
        }
    }

    /** 尽力获取当前用户 ID；无认证上下文（如系统调用/测试）时返回 null，供审计 operator 容错使用。 */
    private Long getCurrentUserIdOrNull() {
        try {
            return SecurityUtil.getCurrentUserId();
        } catch (BusinessException e) {
            return null;
        }
    }

    @Override
    public void assertCourseOwnership(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        boolean isAdmin = SecurityUtil.isAdmin();
        // TEACHER 必须为课程 owner；ADMIN 跳过校验
        if (!isAdmin && !course.getTeacherId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    @Override
    public void assertStudentInTeachersCourses(Long teacherId, Long studentId) {
        long count = enrollmentRepository.countByTeacherAndStudent(teacherId, studentId,
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.COMPLETED.getValue());
        if (count == 0) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "该学生不在您的授课课程中");
        }
    }

    /**
     * Phase A-4 (P0-5): 获取单条选课详情。
     * 仅装配数据；角色级权限校验（本人/课主/ADMIN/ACADEMIC）由 EnrollmentController 执行。
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsByCourseId(Long courseId) {
        return enrollmentRepository.findActiveUserIdsByCourseId(
                courseId,
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.APPROVED.getValue(),
                EnrollmentStatus.COMPLETED.getValue());
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentVO getEnrollmentDetail(Long id) {
        Enrollment enrollment = enrollmentRepository.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        return convertToVO(enrollment);
    }

    /** 课程 Integer 状态码 → 中文描述（消除 3 处重复 if-else 链） */
    private static String describeCourseStatus(Integer status) {
        if (status == null) return "未知";
        CourseStatus cs = CourseStatus.fromCode(status);
        if (cs == null) return "状态不可选";
        switch (cs) {
            case DRAFT: return "未发布";
            case PENDING_REVIEW: return "审核中";
            case APPROVED: return "已通过";
            case REJECTED: return "已驳回";
            case PUBLISHED: return "已发布";
            case CLOSED: return "已下架";
            case ARCHIVED: return "已归档";
            default: return "状态不可选";
        }
    }

}

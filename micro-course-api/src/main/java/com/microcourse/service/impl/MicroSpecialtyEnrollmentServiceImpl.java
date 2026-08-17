package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyClassImportResultVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyClassImportResultVO.ClassImportItemVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CertificateService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.MicroSpecialtyEnrollmentQueryService;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import com.microcourse.service.MicroSpecialtyProgressService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MicroSpecialtyEnrollmentServiceImpl implements MicroSpecialtyEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyEnrollmentServiceImpl.class);
    private static final int BATCH_SIZE = 100;

    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final EnrollmentRepository courseEnrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ClassesRepository classesRepository;
    private final NotificationService notificationService;
    private final EnrollmentService enrollmentService;
    private final MicroSpecialtyService msService;
    private final CertificateService certificateService;
    private final MicroSpecialtyEnrollmentQueryService queryService;
    private final MicroSpecialtyProgressService progressService;
    private final MicroSpecialtyEnrollmentService self;

    public MicroSpecialtyEnrollmentServiceImpl(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                               MicroSpecialtyRepository msRepository,
                                               MicroSpecialtyCourseRepository msCourseRepository,
                                               EnrollmentRepository courseEnrollmentRepository,
                                               CourseRepository courseRepository,
                                               UserRepository userRepository,
                                               ClassesRepository classesRepository,
                                               NotificationService notificationService,
                                               EnrollmentService enrollmentService,
                                               @Lazy MicroSpecialtyService msService,
                                               CertificateService certificateService,
                                               MicroSpecialtyEnrollmentQueryService queryService,
                                               MicroSpecialtyProgressService progressService,
                                               @Lazy MicroSpecialtyEnrollmentService self) {
        this.enrollmentRepository = enrollmentRepository;
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.classesRepository = classesRepository;
        this.notificationService = notificationService;
        this.enrollmentService = enrollmentService;
        this.msService = msService;
        this.certificateService = certificateService;
        this.queryService = queryService;
        this.progressService = progressService;
        this.self = self;
    }

    private void assertStudentOperator(Long userId) {
        User user = userRepository.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        if (user.getRole() != UserRole.STUDENT || !SecurityUtil.hasRole(UserRole.STUDENT.name())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅学生可报名微专业");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO apply(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // BUG-002 fix: 区分"未开放招生"和"已结束"两种错误场景
        String msStatus = ms.getStatus();
        if (!"RECRUITING".equals(msStatus)) {
            if ("DRAFT".equals(msStatus) || "PENDING_REVIEW".equals(msStatus) || "APPROVED".equals(msStatus)) {
                throw new BusinessException(ErrorCode.MS_ENROLLMENT_CLOSED, "微专业当前未在招生期");
            } else {
                throw new BusinessException(ErrorCode.MS_ENROLLMENT_CLOSED, "微专业已结束，无法操作");
            }
        }

        // Fix 2: 人数上限校验
        if (ms.getMaxStudents() != null && ms.getMaxStudents() > 0
                && ms.getStudentCount() != null && ms.getStudentCount() >= ms.getMaxStudents()) {
            throw new BusinessException(ErrorCode.MS_MAX_STUDENTS_REACHED);
        }

        Long userId = SecurityUtil.getCurrentUserId();
        assertStudentOperator(userId);

        // 检查是否已有有效报名
        Long existing = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyEnrollment::getUserId, userId)
                        .notIn(MicroSpecialtyEnrollment::getStatus, "REJECTED", "DROPPED", "FAILED"));
        if (existing > 0) throw new BusinessException(ErrorCode.MS_DUPLICATE_ENROLL);

        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        en.setMicroSpecialtyId(msId);
        en.setUserId(userId);
        en.setSource("SELF_APPLY");
        en.setStatus("PENDING");
        en.setAppliedAt(LocalDateTime.now());
        en.setProgress(BigDecimal.ZERO);
        en.setCreditsEarned(BigDecimal.ZERO);
        en.setCoursesCompleted(0);
        en.setCoursesRequired(0);
        en.setCreatedAt(LocalDateTime.now());
        en.setUpdatedAt(LocalDateTime.now());
        en.setVersion(0);
        enrollmentRepository.insert(en);

        // 通知 LEAD
        if (ms.getLeadTeacherId() != null) {
            User student = userRepository.selectById(userId);
            String studentName = student != null ? student.getRealName() : "学生";
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ENROLLMENT_PENDING,
                    "微专业报名申请", studentName + " 申请加入《" + ms.getTitle() + "》", msId);
        }

        return queryService.toVO(en, ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO approve(Long id) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // 校验操作用户是该微专业的负责人
        Long userId = SecurityUtil.getCurrentUserId();
        /* ---- 【C-19修复】approve() Service 层排除 ACADEMIC ---- */
        /* 【根因】Service 层二次校验只允许 isLeadOf() || isAdmin()，排除了 ACADEMIC 角色
         * 【修复】增加 hasRole("ACADEMIC") 放行条件，与 Controller 的 @PreAuthorize 统一
         * 【防止再发】Service 层鉴权必须与 @PreAuthorize 角色集合一致 */
        if (!msService.isLeadOf(en.getMicroSpecialtyId(), userId)
                && !SecurityUtil.isAdmin()
                && !SecurityUtil.hasRole("ACADEMIC")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可审批报名");
        }

        if (!"PENDING".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批");
        }

        // P1-2 修复: 在 update 前做终态检查,防止 enrollment→APPROVED 时 MS 已关闭
        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus())) {
            // BUG-004 fix: 统一使用 MS_STATUS_INVALID(17003)
throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业已处于终态，无法操作");
        }

        // P1-C-1: 校验 MS 必须是 RECRUITING 状态
        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业当前未在招生中，无法通过审批");
        }

        // P1-C-12-05 fix: 审批时双重检查人数上限
        // apply() 时已检查人数上限,但可能存在两个 PENDING 申请,
        // 当第一个被审批通过后 studentCount+1, 第二个在审批时 studentCount 仍为旧值
        // 必须在审批时再检查一次,确保不超过 maxStudents
        if (ms.getMaxStudents() != null && ms.getMaxStudents() > 0) {
            long currentCount = enrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                    .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, en.getMicroSpecialtyId())
                    .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS"));
            if (currentCount >= ms.getMaxStudents()) {
                throw new BusinessException(ErrorCode.MS_MAX_STUDENTS_REACHED,
                    "微专业已招满（" + currentCount + "/" + ms.getMaxStudents() + "）");
            }
        }

        int oldVersion = en.getVersion();
        int affected = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, id)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, "PENDING")
                        .set(MicroSpecialtyEnrollment::getStatus, "APPROVED")
                        .set(MicroSpecialtyEnrollment::getApprovedAt, LocalDateTime.now())
                        .set(MicroSpecialtyEnrollment::getApprovedBy, SecurityUtil.getCurrentUserId())
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // 自动 enroll 必修课（§9.5）
        List<MicroSpecialtyCourse> requiredCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId())
                        .eq(MicroSpecialtyCourse::getIsRequired, true));

        // 自动 enroll 必修课（§9.5），失败课程计入 pendingCourses
        List<Map<String, Object>> pendingList = new ArrayList<>();

        for (MicroSpecialtyCourse mc : requiredCourses) {
            try {
                // §9.10: 检查是否已有有效课程选课（含已通过的旧成绩）
                Enrollment existingEnroll = courseEnrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));

                BigDecimal minScore = mc.getMinScore() != null ? mc.getMinScore() : BigDecimal.valueOf(60);
                boolean alreadyPassed = existingEnroll != null
                        && existingEnroll.getFinalScore() != null
                        && existingEnroll.getFinalScore().compareTo(minScore) >= 0;

                if (alreadyPassed) {
                    // §9.10: 已修课程学分认可 → 计入完成统计，跳过 enroll
                    en.setCoursesCompleted(en.getCoursesCompleted() != null ? en.getCoursesCompleted() + 1 : 1);
                    en.setCreditsEarned((en.getCreditsEarned() != null ? en.getCreditsEarned() : BigDecimal.ZERO)
                            .add(mc.getCredits() != null ? mc.getCredits() : BigDecimal.ZERO));
                } else if (existingEnroll == null) {
                    // 没有选课记录 → 自动 enroll
                    EnrollmentCreateRequest enrollReq = new EnrollmentCreateRequest();
                    enrollReq.setCourseId(mc.getCourseId());
                    enrollReq.setUserId(en.getUserId());
                    enrollReq.setSourceChannel("MICRO_SPECIALTY_AUTO");
                    // REQUIRES_NEW：选课失败回滚内层事务，不污染申报共享事务
                    enrollmentService.enrollInNewTransaction(enrollReq);
                }
                // else: 已有选课但未通过 → 保留现有选课，不重复 enroll

                en.setCoursesRequired(en.getCoursesRequired() != null ? en.getCoursesRequired() + 1 : 1);
            } catch (BusinessException e) {
                // 不可自动 enroll（前置/容量/冲突等），记录到 pendingCourses
                Course c = courseRepository.selectById(mc.getCourseId());
                Map<String, Object> item = new HashMap<>();
                item.put("courseId", mc.getCourseId());
                item.put("courseName", c != null ? c.getTitle() : "课程#" + mc.getCourseId());
                item.put("reason", e.getMessage());
                pendingList.add(item);
                log.info("[MS approve] student={} course={} -> pending: {}",
                        en.getUserId(), mc.getCourseId(), e.getMessage());
            } catch (Exception e) {
                Course c = courseRepository.selectById(mc.getCourseId());
                Map<String, Object> item = new HashMap<>();
                item.put("courseId", mc.getCourseId());
                item.put("courseName", c != null ? c.getTitle() : "课程#" + mc.getCourseId());
                item.put("reason", e.getMessage() != null ? e.getMessage() : "未知错误");
                pendingList.add(item);
                log.warn("自动 enroll 课程失败: courseId={}, userId={}", mc.getCourseId(), en.getUserId(), e);
            }
        }

        // P1C-079: 必修课自动选课异常 → 回滚整个审批事务,保证一致性
        if (!pendingList.isEmpty()) {
            StringBuilder errMsg = new StringBuilder("必修课自动选课失败，审批已回滚。失败课程：");
            for (int i = 0; i < pendingList.size(); i++) {
                Map<String, Object> item = pendingList.get(i);
                if (i > 0) errMsg.append("；");
                errMsg.append(item.get("courseName")).append("(").append(item.get("reason")).append(")");
            }
            log.error("[MS approve] 必修课自动选课失败, 回滚审批: enrollmentId={}, studentId={}, pending={}",
                    id, en.getUserId(), pendingList);
            // P1C-079: 通知学生异常情况
            try {
                notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_REJECTED,
                        "审批未通过", "您的微专业报名审批失败：" + errMsg.toString(), en.getMicroSpecialtyId());
            } catch (Exception e) {
                log.warn("[MS approve] 通知审批失败异常学生失败: enrollmentId={}", id, e);
            }
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, errMsg.toString());
        }

        // Fix 3: 持久化已认可学分统计到 DB（版本锁，防止并发覆盖）
        int affected2 = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, id)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion + 1)
                        .set(MicroSpecialtyEnrollment::getCoursesCompleted, en.getCoursesCompleted())
                        .set(MicroSpecialtyEnrollment::getCreditsEarned, en.getCreditsEarned())
                        .set(MicroSpecialtyEnrollment::getCoursesRequired, en.getCoursesRequired())
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected2 == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        // Fix 5: 更新 student_count（乐观锁 + affected 校验）
        if (ms != null) {
            int msOldVersion = ms.getVersion();
            int msAffected = msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, ms.getId())
                            .eq(MicroSpecialty::getVersion, msOldVersion)
                            .setSql("student_count = COALESCE(student_count, 0) + 1")
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (msAffected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }
        }

        // 通知学生
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_APPROVED,
                "报名已通过", "您的微专业报名已通过", en.getMicroSpecialtyId());

        en.setStatus("APPROVED");
        return queryService.toVO(en, ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // Fix 1: 微专业终态校验
        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus())) {
            // BUG-004 fix: 统一使用 MS_STATUS_INVALID(17003)
throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业已处于终态，无法操作");
        }

        // 校验操作用户是该微专业的负责人
        Long userId = SecurityUtil.getCurrentUserId();
        /* ---- 【C-19修复】reject() Service 层排除 ACADEMIC ---- */
        /* 【根因】【防止再发】同 approve() 修复 */
        if (!msService.isLeadOf(en.getMicroSpecialtyId(), userId)
                && !SecurityUtil.isAdmin()
                && !SecurityUtil.hasRole("ACADEMIC")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可驳回报名");
        }

        if (!"PENDING".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可驳回");
        }

        int oldVersion = en.getVersion();
        int affected = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, id)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, "PENDING")
                        .set(MicroSpecialtyEnrollment::getStatus, "REJECTED")
                        .set(MicroSpecialtyEnrollment::getDropReason, com.microcourse.util.XssSanitizer.sanitizePlainText(reason))
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        String safeReason = reason != null ? com.microcourse.util.XssSanitizer.sanitizePlainText(reason) : "未填写";
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_REJECTED,
                "报名被驳回", "您的微专业报名被驳回，原因：" + safeReason, en.getMicroSpecialtyId());
    }

    @Override
    public MicroSpecialtyClassImportResultVO classImportBatch(Long microSpecialtyId, java.util.List<Long> classIds) {
        MicroSpecialtyClassImportResultVO result = new MicroSpecialtyClassImportResultVO();
        List<ClassImportItemVO> successList = new ArrayList<>();
        List<ClassImportItemVO> failedList = new ArrayList<>();

        for (Long classId : classIds) {
            try {
                int imported = self.classImport(microSpecialtyId, classId);
                Classes cls = classesRepository.selectById(classId);
                successList.add(new ClassImportItemVO(classId, cls != null ? cls.getName() : "未知班级", imported, null));
            } catch (Exception e) {
                log.error("[MS classImportBatch] 班级 {} 导入失败，跳过继续处理下一个班级", classId, e);
                Classes cls = classesRepository.selectById(classId);
                failedList.add(new ClassImportItemVO(classId, cls != null ? cls.getName() : "未知班级", 0, e.getMessage()));
            }
        }

        result.setTotalCount(classIds.size());
        result.setSuccessCount(successList.size());
        result.setFailedCount(failedList.size());
        result.setSuccessList(successList);
        result.setFailedList(failedList);
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class, timeout = 300)
    public int classImport(Long msId, Long classId) {
        // 1. 锁定并校验微专业状态、容量、已有选课
        ClassImportContext ctx = prepareClassImportContext(msId, classId);
        if (ctx.students.isEmpty()) return 0;

        // 2. 逐学生执行：建选课记录 + 课程 REQUIRES_NEW 自动选课
        ClassImportResult result = importStudentsForClass(ctx);

        // 3. 乐观锁更新 student_count
        if (result.imported > 0) {
            updateStudentCountWithVersionGuard(ctx.ms, result.imported);
        }

        // 4. 通知学生 / LEAD / ACADEMIC
        notifyAfterClassImport(ctx, result);

        return result.imported;
    }

    /**
     * P1-C 上下文快照：classImport 步骤 1 的所有查询结果，避免在主方法中横跨 ~30 行重复 SELECT。
     */
    private record ClassImportContext(
            MicroSpecialty ms,
            List<User> students,
            List<Long> courseIds,
            Set<Long> existingUserIds,
            int coursesRequired) {}

    /**
     * P1-C 结果快照：classImport 步骤 2/3 的聚合指标，避免 4 个计数变量在主方法签名中漂移。
     */
    private record ClassImportResult(
            int imported,
            int totalPendingCount,
            int studentsWithPending,
            Map<Long, List<PendingCourseJsonUtil.PendingCourseItem>> pendingByUser) {}

    /**
     * 步骤 1: 锁定 MS 行，校验状态/容量/已有选课，准备学生列表与微专业课程列表。
     */
    private ClassImportContext prepareClassImportContext(Long msId, Long classId) {
        MicroSpecialty ms = lockAndValidateMsForClassImport(msId);

        // 名额占用口径与 uk_mse_active 一致（排除终态 REJECTED/DROPPED/FAILED）
        Set<Long> existingUserIds = loadExistingEnrollmentUserIds(msId);

        Integer remainingSlots = computeRemainingSlots(ms, existingUserIds.size());

        List<User> students = userRepository.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClassId, classId)
                        .eq(User::getRole, UserRole.STUDENT));

        long newStudentCount = students.stream()
                .filter(s -> !existingUserIds.contains(s.getId()))
                .count();
        if (remainingSlots != null && newStudentCount > remainingSlots) {
            throw new BusinessException(ErrorCode.MS_MAX_STUDENTS_REACHED,
                "剩余名额不足，剩余 " + remainingSlots + " 个，待导入 " + newStudentCount + " 人");
        }

        List<Long> courseIds = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId))
                .stream()
                .map(MicroSpecialtyCourse::getCourseId)
                .collect(Collectors.toList());

        return new ClassImportContext(ms, students, courseIds, existingUserIds, courseIds.size());
    }

    /**
     * 锁定微专业行 + 校验状态 + 容量（保留层叠校验的语义）。
     */
    private MicroSpecialty lockAndValidateMsForClassImport(Long msId) {
        MicroSpecialty ms = msRepository.selectForUpdate(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可班级导入");
        }
        return ms;
    }

    /**
     * 加载"已占名额"的选课用户 ID（排除终态，与 uk_mse_active 部分唯一索引语义一致）。
     */
    private Set<Long> loadExistingEnrollmentUserIds(Long msId) {
        return enrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId)
                        .notIn(MicroSpecialtyEnrollment::getStatus,
                                "REJECTED", "DROPPED", "FAILED"))
                .stream()
                .map(MicroSpecialtyEnrollment::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * 计算剩余名额；maxStudents 未配置（≤0）返回 null 表示不限制。
     */
    private Integer computeRemainingSlots(MicroSpecialty ms, int currentEnrolled) {
        if (ms.getMaxStudents() == null || ms.getMaxStudents() <= 0) {
            return null;
        }
        int remaining = (int) (ms.getMaxStudents() - currentEnrolled);
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.MS_MAX_STUDENTS_REACHED, "微专业已满员，无法导入班级");
        }
        return remaining;
    }

    /**
     * 步骤 2: 逐学生执行 — 构建 MS 选课记录、批量 REQUIRES_NEW 自动选课、按 BATCH_SIZE 落库。
     * 单条选课失败被捕获后转 pendingCourses JSON，不污染班级导入事务。
     */
    private ClassImportResult importStudentsForClass(ClassImportContext ctx) {
        int imported = 0;
        int totalPendingCount = 0;
        int studentsWithPending = 0;
        Map<Long, List<PendingCourseJsonUtil.PendingCourseItem>> pendingByUser = new HashMap<>();
        List<MicroSpecialtyEnrollment> batch = new ArrayList<>(BATCH_SIZE);

        for (User student : ctx.students) {
            if (ctx.existingUserIds.contains(student.getId())) {
                continue;
            }

            List<PendingCourseJsonUtil.PendingCourseItem> pending = enrollStudentInMsCourses(
                    student, ctx.courseIds);

            MicroSpecialtyEnrollment en = buildClassImportEnrollment(student, ctx);
            if (!pending.isEmpty()) {
                en.setPendingCourses(PendingCourseJsonUtil.toPendingJson(pending));
                pendingByUser.put(student.getId(), pending);
                totalPendingCount += pending.size();
                studentsWithPending++;
            }

            batch.add(en);
            int flushed = flushBatchIfFull(batch);
            imported += flushed;
            batch.clear();
            if (flushed == 0) {
                // batch 未满，继续累积
            }
        }

        // 处理剩余
        if (!batch.isEmpty()) {
            int flushed = flushBatch(batch);
            imported += flushed;
        }

        return new ClassImportResult(imported, totalPendingCount, studentsWithPending, pendingByUser);
    }

    /**
     * 批量 flushBatch插入 MS 选课记录；当 batch 满 BATCH_SIZE 时返回实际写入数，调用方负责清空。
     * 修复原代码 bug：原版 flushed BATCH_SIZE 后未清空 batch，导致后续被重复插入。
     */
    private int flushBatchIfFull(List<MicroSpecialtyEnrollment> batch) {
        if (batch.size() < BATCH_SIZE) {
            return 0;
        }
        return flushBatch(batch);
    }

    /**
     * 实际批量插入 MS 选课记录。
     */
    private int flushBatch(List<MicroSpecialtyEnrollment> batch) {
        int size = batch.size();
        for (MicroSpecialtyEnrollment e : batch) {
            enrollmentRepository.insert(e);
        }
        return size;
    }

    /**
     * 构造 MS 选课记录（CLASS_IMPORT 来源，APPROVED 状态，初始进度 0）。
     */
    private MicroSpecialtyEnrollment buildClassImportEnrollment(User student, ClassImportContext ctx) {
        MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
        LocalDateTime now = LocalDateTime.now();
        en.setMicroSpecialtyId(ctx.ms.getId());
        en.setUserId(student.getId());
        en.setSource("CLASS_IMPORT");
        en.setClassId(student.getClassId());
        en.setStatus("APPROVED");
        en.setAppliedAt(now);
        en.setApprovedAt(now);
        en.setProgress(BigDecimal.ZERO);
        en.setCreditsEarned(BigDecimal.ZERO);
        en.setCoursesCompleted(0);
        en.setCoursesRequired(ctx.coursesRequired);
        en.setCreatedAt(now);
        en.setUpdatedAt(now);
        en.setVersion(0);
        return en;
    }

    /**
     * 在 REQUIRES_NEW 内层事务中逐门课程调用选课服务。
     * 单门失败 → 转 pendingCourses（前置/容量/已选等原因），不影响 MS 主事务与该生其他课程。
     */
    private List<PendingCourseJsonUtil.PendingCourseItem> enrollStudentInMsCourses(
            User student, List<Long> courseIds) {
        List<PendingCourseJsonUtil.PendingCourseItem> pending = new ArrayList<>();
        for (Long courseId : courseIds) {
            try {
                EnrollmentCreateRequest req = new EnrollmentCreateRequest();
                req.setUserId(student.getId());
                req.setCourseId(courseId);
                req.setSourceChannel("MICRO_SPECIALTY");
                enrollmentService.enrollInNewTransaction(req);
            } catch (BusinessException e) {
                pending.add(buildPendingItem(courseId, e.getMessage()));
                log.info("[MS classImport] student={} course={} -> pending: {}",
                        student.getId(), courseId, e.getMessage());
            } catch (Exception e) {
                pending.add(buildPendingItem(courseId, e.getMessage() != null ? e.getMessage() : "未知错误"));
                log.warn("[MS classImport] student={} course={} unexpected: {}",
                        student.getId(), courseId, e.getMessage());
            }
        }
        return pending;
    }

    /**
     * 构造"待人工处理课程"记录：包含 courseId、可读 courseName、失败原因。
     */
    private PendingCourseJsonUtil.PendingCourseItem buildPendingItem(Long courseId, String reason) {
        String courseName = resolveCourseName(courseId);
        return new PendingCourseJsonUtil.PendingCourseItem(courseId, courseName, reason);
    }

    /**
     * 解析课程名称：DB 查询失败时回退到 "课程#id" 占位符，避免打断导入流程。
     */
    private String resolveCourseName(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        return course != null ? course.getTitle() : "课程#" + courseId;
    }

    /**
     * 步骤 3: 乐观锁更新 student_count（version 守门，并发失败抛 MS_CONCURRENT_MODIFICATION）。
     */
    private void updateStudentCountWithVersionGuard(MicroSpecialty ms, int imported) {
        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, ms.getId())
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .setSql("student_count = COALESCE(student_count, 0) + " + imported)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION,
                    "微专业状态已被并发修改，请重试");
        }
    }

    /**
     * 步骤 4: 通知 — 学生（含 pending 提示）/ LEAD / ACADEMIC（>10% 待处理时预警）。
     */
    private void notifyAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        notifyStudentsAfterClassImport(ctx, result);
        notifyLeadAfterClassImport(ctx, result);
        notifyAcademicOnPendingThreshold(ctx, result);
    }

    /**
     * 通知本批次新导入学生，含待处理课程数提示。
     */
    private void notifyStudentsAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        for (User student : ctx.students) {
            if (ctx.existingUserIds.contains(student.getId())) {
                continue;
            }
            List<PendingCourseJsonUtil.PendingCourseItem> pending = result.pendingByUser.get(student.getId());
            String tip = (pending != null && !pending.isEmpty())
                    ? "，其中 " + pending.size() + " 门课程需您或负责人后续处理（前置/容量/已选）"
                    : "";
            notificationService.notifyAsync(student.getId(),
                    NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                    "已加入微专业",
                    "您已被批量导入微专业《" + ctx.ms.getTitle() + "》" + tip,
                    ctx.ms.getId());
        }
    }

    /**
     * 通知 MS 项目负责人 — 项目总负责要求 §9.1 step 8。
     */
    private void notifyLeadAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        if (ctx.ms.getLeadTeacherId() == null) {
            return;
        }
        notificationService.notifyAsync(ctx.ms.getLeadTeacherId(),
                NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                "班级导入完成",
                String.format("班级已成功导入 %d 名学生（%d 门课程需人工处理）",
                        result.imported, result.totalPendingCount),
                ctx.ms.getId());
    }

    /**
     * 待处理课程占比 >10% 时通知 ACADEMIC — 防止问题班级悄悄上线无人察觉。
     */
    private void notifyAcademicOnPendingThreshold(ClassImportContext ctx, ClassImportResult result) {
        if (result.imported == 0 || result.studentsWithPending * 10 <= result.imported) {
            return;
        }
        List<User> academicUsers = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        for (User au : academicUsers) {
            notificationService.notifyAsync(au.getId(),
                    NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                    "微专业班级导入预警",
                    String.format("微专业《%s》班级导入中 %d/%d 学生存在待处理课程，建议关注",
                            ctx.ms.getTitle(), result.studentsWithPending, result.imported),
                    ctx.ms.getId());
        }
    }

    // PendingCourseJsonUtil.PendingCourseItem / toPendingJson / jsonEscape 已提取到 PendingCourseJsonUtil

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void drop(Long id, boolean cascade, String reason) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // 仅 APPROVED 或 IN_PROGRESS 可退出
        if (!"APPROVED".equals(en.getStatus()) && !"IN_PROGRESS".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许退出");
        }

        // 微专业终态校验
        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms != null && ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus()))) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业已取消或归档，无法退出修读");
        }

        // IDOR 校验：仅本人或 ADMIN 可退出
        Long userId = SecurityUtil.getCurrentUserId();
        boolean isAdmin = SecurityUtil.isAdmin();
        if (!en.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅本人可操作退出");
        }

        int oldVersion = en.getVersion();
        int affected = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, id)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .set(MicroSpecialtyEnrollment::getStatus, "DROPPED")
                        .set(MicroSpecialtyEnrollment::getDropReason, reason)
                        .set(MicroSpecialtyEnrollment::getDroppedAt, LocalDateTime.now())
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // student_count 防负（复用上方已加载的 ms）
        if (ms != null && ms.getStudentCount() != null && ms.getStudentCount() > 0) {
            int msOldVersion = ms.getVersion();
            int msAffected = msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, ms.getId())
                            .eq(MicroSpecialty::getVersion, msOldVersion)
                            .setSql("student_count = student_count - 1")
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            // Fix 5: affected == 0 校验
            if (msAffected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }
        }

        // 通知 LEAD 和 student
        if (ms != null && ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ENROLLMENT_DROPPED,
                    "学生已退出微专业", "学生已退出《" + ms.getTitle() + "》", en.getMicroSpecialtyId());
        }
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_DROPPED,
                "已退出微专业", "您已退出微专业《" + ms.getTitle() + "》", en.getMicroSpecialtyId());

        if (cascade || isAdmin) {
            List<MicroSpecialtyCourse> msCourses = msCourseRepository.selectList(
                    new LambdaQueryWrapper<MicroSpecialtyCourse>()
                            .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId()));
            for (MicroSpecialtyCourse mc : msCourses) {
                Enrollment courseEn = courseEnrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .in(Enrollment::getSourceChannel, "MICRO_SPECIALTY", "MICRO_SPECIALTY_AUTO")
                                .in(Enrollment::getEnrollmentStatus,
                                        EnrollmentStatus.legacyActiveWith(
                                                EnrollmentStatus.WAITLIST.getValue(),
                                                EnrollmentStatus.SUSPENDED.getValue())));
                if (courseEn != null) {
                    enrollmentService.cancelEnrollment(courseEn.getId(), userId);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO reapply(Long id) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        Long userId = SecurityUtil.getCurrentUserId();
        assertStudentOperator(userId);
        // IDOR 校验：仅本人可操作
        if (!en.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅本人可重新申请");
        }

        // 仅 REJECTED/DROPPED/FAILED → PENDING（§2.2）
        String currentStatus = en.getStatus();
        if (!"REJECTED".equals(currentStatus) && !"DROPPED".equals(currentStatus) && !"FAILED".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已驳回/已退出/未通过状态可重新申请");
        }

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus())) {
            // BUG-004 fix: 统一使用 MS_STATUS_INVALID(17003)
throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业已处于终态，无法操作");
        }
        // P1C-038: 校验微专业是否仍处于招生中
        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_ENROLLMENT_CLOSED, "微专业当前未在招生中，无法重新申请");
        }

        int oldVersion = en.getVersion();
        int affected = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, id)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, currentStatus)
                        .set(MicroSpecialtyEnrollment::getStatus, "PENDING")
                        .set(MicroSpecialtyEnrollment::getProgress, BigDecimal.ZERO)
                        .set(MicroSpecialtyEnrollment::getCreditsEarned, BigDecimal.ZERO)
                        .set(MicroSpecialtyEnrollment::getCoursesCompleted, 0)
                        .set(MicroSpecialtyEnrollment::getAppliedAt, LocalDateTime.now())
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // 通知 LEAD
        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ENROLLMENT_REAPPLIED,
                    "学生重新申请微专业", "学生重新申请加入《" + ms.getTitle() + "》", en.getMicroSpecialtyId());
        }
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_PENDING,
                "重新申请已提交", "您的微专业重新申请已提交，请等待审批", en.getMicroSpecialtyId());

        en.setStatus("PENDING");
        return queryService.toVO(en, ms);
    }

    @Override
    public List<MicroSpecialtyEnrollmentVO> getMyEnrollments() {
        return queryService.getMyEnrollments();
    }

    @Override
    public PageResult<MicroSpecialtyEnrollmentVO> listEnrollments(Long msId, int page, int size, String status) {
        return queryService.listEnrollments(msId, page, size, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issueCertificate(Long enrollmentId) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(enrollmentId);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // 有认证用户时执行人工发证权限校验；无认证上下文时允许内部任务链路自动发证
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                && !SecurityUtil.isAdminOrAcademic()) {
            msService.requireLeadOf(en.getMicroSpecialtyId());
        }

        if (!"COMPLETED".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_CERT_NOT_READY);
        }

        // 幂等检查：已发过证书
        if (en.getCertificateId() != null) return;

        // P0-1 修复：委托 CertificateService 创建证书记录（写入 certificates 表，含通知）
        certificateService.issueMicroSpecialtyCertificate(en.getUserId(), en.getMicroSpecialtyId(), enrollmentId);

        // 重新读取 enrollment（certificateService 已更新 certificateId + version）
        en = enrollmentRepository.selectById(enrollmentId);

        // 更新状态为 CERTIFIED
        int oldVersion = en.getVersion();
        /* ---- 【C-18修复】issueCertificate 并发未检查 affected rows ---- */
        /* 【根因】enrollmentRepository.update(null, wrapper) 的返回值被忽略，
         *        并发场景下可能未命中任何行（version 或 status 不匹配）但操作被视为成功
         * 【修复】增加 affected 检查，为 0 时抛出 MS_CONCURRENT_MODIFICATION
         * 【防止再发】所有 update/delete 操作必须检查 affected rows */
        int certAffected = enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                        .set(MicroSpecialtyEnrollment::getStatus, "CERTIFIED")
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (certAffected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateProgress(Long enrollmentId) {
        progressService.aggregateProgress(enrollmentId);
    }

}

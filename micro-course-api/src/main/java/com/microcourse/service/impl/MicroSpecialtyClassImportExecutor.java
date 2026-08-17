package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 微专业班级导入执行器 — 从 {@link MicroSpecialtyEnrollmentServiceImpl} 拆出，集中处理
 * 锁定 / 容量校验 / 逐学生导入 / 乐观锁更新 / 通知的完整流程。
 *
 * <p>设计目标：
 * <ul>
 *   <li>消除 {@code MicroSpecialtyEnrollmentServiceImpl} 中的 800+ 行 ServiceImpl 体积</li>
 *   <li>提供可独立单元测试的纯函数式 API（构造函数注入依赖）</li>
 *   <li>不可变 record 传递上下文快照，避免共享可变状态</li>
 * </ul>
 *
 * @author refactor 2026-08-17
 */
public class MicroSpecialtyClassImportExecutor {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyClassImportExecutor.class);

    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentService enrollmentService;
    private final NotificationService notificationService;
    private final int batchSize;

    public MicroSpecialtyClassImportExecutor(MicroSpecialtyRepository msRepository,
                                             MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                             UserRepository userRepository,
                                             MicroSpecialtyCourseRepository msCourseRepository,
                                             CourseRepository courseRepository,
                                             EnrollmentService enrollmentService,
                                             NotificationService notificationService,
                                             int batchSize) {
        this.msRepository = msRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.msCourseRepository = msCourseRepository;
        this.courseRepository = courseRepository;
        this.enrollmentService = enrollmentService;
        this.notificationService = notificationService;
        this.batchSize = batchSize;
    }

    /**
     * 完整流程：准备 → 导入 → 更新 → 通知
     */
    public ClassImportResult run(Long msId, Long classId) {
        ClassImportContext ctx = prepareClassImportContext(msId, classId);
        if (ctx.students.isEmpty()) {
            return new ClassImportResult(0, 0, 0, new HashMap<>());
        }

        ClassImportResult result = importStudentsForClass(ctx);

        if (result.imported() > 0) {
            updateStudentCountWithVersionGuard(ctx.ms, result.imported());
        }

        notifyAfterClassImport(ctx, result);
        return result;
    }

    /**
     * 步骤 1: 锁定 MS 行，校验状态/容量/已有选课。
     */
    ClassImportContext prepareClassImportContext(Long msId, Long classId) {
        MicroSpecialty ms = lockAndValidateMsForClassImport(msId);

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

    private MicroSpecialty lockAndValidateMsForClassImport(Long msId) {
        MicroSpecialty ms = msRepository.selectForUpdate(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可班级导入");
        }
        return ms;
    }

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
     */
    ClassImportResult importStudentsForClass(ClassImportContext ctx) {
        int imported = 0;
        int totalPendingCount = 0;
        int studentsWithPending = 0;
        Map<Long, List<PendingCourseJsonUtil.PendingCourseItem>> pendingByUser = new HashMap<>();
        List<MicroSpecialtyEnrollment> batch = new ArrayList<>(batchSize);

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
            if (flushed > 0) {
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            imported += flushBatch(batch);
        }

        return new ClassImportResult(imported, totalPendingCount, studentsWithPending, pendingByUser);
    }

    /**
     * 当 batch满 BATCH_SIZE 时返回实际写入数。
     * 修复原代码 bug：原版 flushed BATCH_SIZE 后未清空 batch，导致后续被重复插入。
     */
    private int flushBatchIfFull(List<MicroSpecialtyEnrollment> batch) {
        if (batch.size() < batchSize) {
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
     * 在 REQUIRES_NEW 内层事务中逐门列课程调用选课服务。
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

    private PendingCourseJsonUtil.PendingCourseItem buildPendingItem(Long courseId, String reason) {
        String courseName = resolveCourseName(courseId);
        return new PendingCourseJsonUtil.PendingCourseItem(courseId, courseName, reason);
    }

    private String resolveCourseName(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        return course != null ? course.getTitle() : "课程#" + courseId;
    }

    /**
     * 步骤 3: 乐观锁更新 student_count。
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
     * 步骤 4: 通知 — 学生 / LEAD / ACADEMIC。
     */
    private void notifyAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        notifyStudentsAfterClassImport(ctx, result);
        notifyLeadAfterClassImport(ctx, result);
        notifyAcademicOnPendingThreshold(ctx, result);
    }

    private void notifyStudentsAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        for (User student : ctx.students) {
            if (ctx.existingUserIds.contains(student.getId())) {
                continue;
            }
            List<PendingCourseJsonUtil.PendingCourseItem> pending = result.pendingByUser().get(student.getId());
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

    private void notifyLeadAfterClassImport(ClassImportContext ctx, ClassImportResult result) {
        if (ctx.ms.getLeadTeacherId() == null) {
            return;
        }
        notificationService.notifyAsync(ctx.ms.getLeadTeacherId(),
                NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                "班级导入完成",
                String.format("班级已成功导入 %d 名学生（%d 门课程需人工处理）",
                        result.imported(), result.totalPendingCount()),
                ctx.ms.getId());
    }

    private void notifyAcademicOnPendingThreshold(ClassImportContext ctx, ClassImportResult result) {
        if (result.imported() == 0 || result.studentsWithPending() * 10 <= result.imported()) {
            return;
        }
        List<User> academicUsers = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        for (User au : academicUsers) {
            notificationService.notifyAsync(au.getId(),
                    NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                    "微专业班级导入预警",
                    String.format("微专业《%s》班级导入中 %d/%d 学生存在待处理课程，建议关注",
                            ctx.ms.getTitle(), result.studentsWithPending(), result.imported()),
                    ctx.ms.getId());
        }
    }

    /**
     * 上下文快照。
     */
    record ClassImportContext(
            MicroSpecialty ms,
            List<User> students,
            List<Long> courseIds,
            Set<Long> existingUserIds,
            int coursesRequired) {}

    /**
     * 结果快照。
     */
    record ClassImportResult(
            int imported,
            int totalPendingCount,
            int studentsWithPending,
            Map<Long, List<PendingCourseJsonUtil.PendingCourseItem>> pendingByUser) {}
}
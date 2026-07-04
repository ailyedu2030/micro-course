package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
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
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
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
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final EnrollmentRepository courseEnrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EnrollmentService enrollmentService;
    private final MicroSpecialtyService msService;
    private final CertificateService certificateService;
    private final MicroSpecialtyEnrollmentQueryService queryService;
    private final MicroSpecialtyProgressService progressService;

    public MicroSpecialtyEnrollmentServiceImpl(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                               MicroSpecialtyRepository msRepository,
                                               MicroSpecialtyCourseRepository msCourseRepository,
                                               MicroSpecialtyTeacherRepository msTeacherRepository,
                                               EnrollmentRepository courseEnrollmentRepository,
                                               CourseRepository courseRepository,
                                               UserRepository userRepository,
                                               NotificationService notificationService,
                                               EnrollmentService enrollmentService,
                                               @Lazy MicroSpecialtyService msService,
                                               CertificateService certificateService,
                                               MicroSpecialtyEnrollmentQueryService queryService,
                                               MicroSpecialtyProgressService progressService) {
        this.enrollmentRepository = enrollmentRepository;
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.enrollmentService = enrollmentService;
        this.msService = msService;
        this.certificateService = certificateService;
        this.queryService = queryService;
        this.progressService = progressService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO apply(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_ENROLLMENT_CLOSED);
        }

        // Fix 2: 人数上限校验
        if (ms.getMaxStudents() != null && ms.getMaxStudents() > 0
                && ms.getStudentCount() != null && ms.getStudentCount() >= ms.getMaxStudents()) {
            throw new BusinessException(ErrorCode.MS_MAX_STUDENTS_REACHED);
        }

        Long userId = SecurityUtil.getCurrentUserId();

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
        if (!msService.isLeadOf(en.getMicroSpecialtyId(), userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可审批报名");
        }

        if (!"PENDING".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批");
        }

        // P1-2 修复: 在 update 前做终态检查,防止 enrollment→APPROVED 时 MS 已关闭
        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        if ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
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
                    enrollmentService.enroll(enrollReq);
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

        // 持久化 pendingCourses（如有失败课程）
        if (!pendingList.isEmpty()) {
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(pendingList);
                enrollmentRepository.update(null,
                        new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                                .eq(MicroSpecialtyEnrollment::getId, id)
                                .set(MicroSpecialtyEnrollment::getPendingCourses, json));
            } catch (Exception e) {
                log.warn("持久化 pendingCourses 失败: enrollmentId={}", id, e);
            }
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
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        // 校验操作用户是该微专业的负责人
        Long userId = SecurityUtil.getCurrentUserId();
        if (!msService.isLeadOf(en.getMicroSpecialtyId(), userId) && !SecurityUtil.isAdmin()) {
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
                        .set(MicroSpecialtyEnrollment::getDropReason, reason)
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_REJECTED,
                "报名被驳回", "您的微专业报名被驳回，原因：" + (reason != null ? reason : "未填写"), en.getMicroSpecialtyId());
    }

    @Override
    // P1-C-12-04 fix: 班级导入大事务超时
    // 50 人班级 × 10 门课程 = 500 次 enroll() 调用在同一 DB 事务中执行
    // PostgreSQL 事务超时默认 30s,大型班级会触发 timeout 导致整批失败
    // 显式设置 5 分钟上限,适配 100 人 × 20 门课程 = 2000 次调用的极端场景
    @Transactional(rollbackFor = Exception.class, timeout = 300)
    public int classImport(Long msId, Long classId) {
        MicroSpecialty ms = msRepository.selectForUpdate(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可班级导入");
        }

        // 查班级学生
        List<User> students = userRepository.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClassId, classId)
                        .eq(User::getRole, UserRole.STUDENT));
        if (students.isEmpty()) return 0;

        // 预查已存在学生
        List<MicroSpecialtyEnrollment> existingList = enrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId)
                        .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS"));
        Set<Long> existingUserIds = existingList.stream()
                .map(MicroSpecialtyEnrollment::getUserId).collect(Collectors.toSet());

        // G2: 加载微专业的所有课程（必修 + 选修），用于自动 enroll
        List<MicroSpecialtyCourse> msCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId));
        List<Long> courseIds = msCourses.stream()
                .map(MicroSpecialtyCourse::getCourseId).collect(Collectors.toList());

        int imported = 0;
        int totalPendingCount = 0;
        int studentsWithPending = 0;
        List<MicroSpecialtyEnrollment> batch = new ArrayList<>(BATCH_SIZE);
        // 收集每个新生的 (userId, pendingCoursesJson)，待主 enrollment 写入后回写
        Map<Long, List<PendingCourseItem>> pendingByUser = new HashMap<>();

        for (User student : students) {
            if (existingUserIds.contains(student.getId())) continue;

            MicroSpecialtyEnrollment en = new MicroSpecialtyEnrollment();
            en.setMicroSpecialtyId(msId);
            en.setUserId(student.getId());
            en.setSource("CLASS_IMPORT");
            en.setClassId(classId);
            en.setStatus("APPROVED");
            en.setAppliedAt(LocalDateTime.now());
            en.setApprovedAt(LocalDateTime.now());
            en.setProgress(BigDecimal.ZERO);
            en.setCreditsEarned(BigDecimal.ZERO);
            en.setCoursesCompleted(0);
            en.setCoursesRequired(msCourses.size());
            en.setCreatedAt(LocalDateTime.now());
            en.setUpdatedAt(LocalDateTime.now());
            en.setVersion(0);

            // G2: 逐门课程前置检查（自动 enroll）
            List<PendingCourseItem> pending = new ArrayList<>();
            for (Long courseId : courseIds) {
                try {
                    EnrollmentCreateRequest req = new EnrollmentCreateRequest();
                    req.setUserId(student.getId());
                    req.setCourseId(courseId);
                    req.setSourceChannel("MICRO_SPECIALTY");
                    enrollmentService.enroll(req);
                } catch (BusinessException e) {
                    // 不可自动 enroll，记录到 pendingCourses（不抛，不阻断主流程）
                    Course course = courseRepository.selectById(courseId);
                    String courseName = (course != null) ? course.getTitle() : ("课程#" + courseId);
                    pending.add(new PendingCourseItem(courseId, courseName, e.getMessage()));
                    log.info("[MS classImport] student={} course={} -> pending: {}",
                            student.getId(), courseId, e.getMessage());
                } catch (Exception e) {
                    Course course = courseRepository.selectById(courseId);
                    String courseName = (course != null) ? course.getTitle() : ("课程#" + courseId);
                    pending.add(new PendingCourseItem(courseId, courseName,
                            e.getMessage() != null ? e.getMessage() : "未知错误"));
                    log.warn("[MS classImport] student={} course={} unexpected: {}",
                            student.getId(), courseId, e.getMessage());
                }
            }

            if (!pending.isEmpty()) {
                String json = toPendingJson(pending);
                en.setPendingCourses(json);
                pendingByUser.put(student.getId(), pending);
                totalPendingCount += pending.size();
                studentsWithPending++;
            }

            batch.add(en);

            if (batch.size() >= BATCH_SIZE) {
                for (MicroSpecialtyEnrollment e : batch) {
                    enrollmentRepository.insert(e);
                }
                imported += batch.size();
                batch.clear();
            }
        }
        // 处理剩余
        if (!batch.isEmpty()) {
            for (MicroSpecialtyEnrollment e : batch) {
                enrollmentRepository.insert(e);
            }
            imported += batch.size();
        }

        // 更新 student_count
        if (imported > 0) {
            int oldVersion = ms.getVersion();
            int affected = msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, msId)
                            .eq(MicroSpecialty::getVersion, oldVersion)
                            .setSql("student_count = COALESCE(student_count, 0) + " + imported)
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (affected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION,
                        "微专业状态已被并发修改，请重试");
            }
        }

        // 通知学生（含 pendingCourses 提示）
        for (User student : students) {
            if (existingUserIds.contains(student.getId())) continue;
            List<PendingCourseItem> pending = pendingByUser.get(student.getId());
            String tip = "";
            if (pending != null && !pending.isEmpty()) {
                tip = "，其中 " + pending.size() + " 门课程需您或负责人后续处理（前置/容量/已选）";
            }
            notificationService.notifyAsync(student.getId(), NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                    "已加入微专业",
                    "您已被批量导入微专业《" + ms.getTitle() + "》" + tip,
                    msId);
        }

        // 通知 LEAD（项目总负责要求 §9.1 step 8）
        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                    "班级导入完成",
                    String.format("班级已成功导入 %d 名学生（%d 门课程需人工处理）",
                            imported, totalPendingCount),
                    msId);
        }

        // 如果 >10% 的学生有 pending，额外通知 ACADEMIC
        if (imported > 0 && (studentsWithPending * 10 > imported)) {
            // 通过 UserRepository 查 ACADEMIC 角色
            List<User> academicUsers = userRepository.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
            for (User au : academicUsers) {
                notificationService.notifyAsync(au.getId(), NotificationType.MS_ENROLLMENT_AUTO_ENROLL,
                        "微专业班级导入预警",
                        String.format("微专业《%s》班级导入中 %d/%d 学生存在待处理课程，建议关注",
                                ms.getTitle(), studentsWithPending, imported),
                        msId);
            }
        }

        return imported;
    }

    /** G2: pendingCourses 内部数据结构（DTO 序列化为 JSON） */
    private static class PendingCourseItem {
        Long courseId;
        String courseName;
        String reason;
        PendingCourseItem(Long courseId, String courseName, String reason) {
            this.courseId = courseId; this.courseName = courseName; this.reason = reason;
        }
    }

    /** G2: List<PendingCourseItem> → JSON 字符串（轻量手写避免引入 Jackson 依赖问题） */
    private String toPendingJson(List<PendingCourseItem> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            PendingCourseItem p = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"courseId\":").append(p.courseId)
              .append(",\"courseName\":").append(jsonEscape(p.courseName))
              .append(",\"reason\":").append(jsonEscape(p.reason)).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonEscape(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

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
        if (!en.getUserId().equals(userId) && !SecurityUtil.isAdmin()) {
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

        // 级联删除课程级 enrollment
        if (cascade) {
            List<MicroSpecialtyCourse> msCourses = msCourseRepository.selectList(
                    new LambdaQueryWrapper<MicroSpecialtyCourse>()
                            .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId()));
            for (MicroSpecialtyCourse mc : msCourses) {
                Enrollment courseEn = courseEnrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
                if (courseEn != null) {
                    courseEnrollmentRepository.update(null,
                            new LambdaUpdateWrapper<Enrollment>()
                                    .eq(Enrollment::getId, courseEn.getId())
                                    .eq(Enrollment::getVersion, courseEn.getVersion())
                                    .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue())
                                    .set(Enrollment::getUpdatedAt, LocalDateTime.now())
                                    .setSql("version = version + 1"));
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO reapply(Long id) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // IDOR 校验：仅本人可操作
        Long userId = SecurityUtil.getCurrentUserId();
        if (!en.getUserId().equals(userId) && !SecurityUtil.isAdmin()) {
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
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
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

        // Fix 4: 权限校验——仅负责人或管理员/教务处可颁发证书
        if (!SecurityUtil.isAdminOrAcademic()) {
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
        enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                        .set(MicroSpecialtyEnrollment::getStatus, "CERTIFIED")
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateProgress(Long enrollmentId) {
        progressService.aggregateProgress(enrollmentId);
    }

}

package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.entity.*;
import com.microcourse.enums.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.repository.*;
import com.microcourse.service.*;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MicroSpecialtyEnrollmentServiceImpl implements MicroSpecialtyEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyEnrollmentServiceImpl.class);
    private static final int BATCH_SIZE = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    public MicroSpecialtyEnrollmentServiceImpl(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                               MicroSpecialtyRepository msRepository,
                                               MicroSpecialtyCourseRepository msCourseRepository,
                                               MicroSpecialtyTeacherRepository msTeacherRepository,
                                               EnrollmentRepository courseEnrollmentRepository,
                                               CourseRepository courseRepository,
                                               UserRepository userRepository,
                                               NotificationService notificationService,
                                               EnrollmentService enrollmentService,
                                               @Lazy MicroSpecialtyService msService) {
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO apply(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_ENROLLMENT_CLOSED);
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
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_INVITE_TEAM,
                    "微专业报名申请", studentName + " 申请加入《" + ms.getTitle() + "》", msId);
        }

        return toVO(en, ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO approve(Long id) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        if (!"PENDING".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批");
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

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());

        // 自动 enroll 必修课（§9.5）
        List<MicroSpecialtyCourse> requiredCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId())
                        .eq(MicroSpecialtyCourse::getIsRequired, true));

        for (MicroSpecialtyCourse mc : requiredCourses) {
            try {
                // 检查是否已有有效课程选课
                Long existingEnroll = courseEnrollmentRepository.selectCount(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .ne(Enrollment::getEnrollmentStatus, "CANCELLED"));
                if (existingEnroll == 0) {
                    EnrollmentCreateRequest enrollReq = new EnrollmentCreateRequest();
                    enrollReq.setCourseId(mc.getCourseId());
                    enrollReq.setUserId(en.getUserId());
                    enrollReq.setSourceChannel("MICRO_SPECIALTY_AUTO");
                    enrollmentService.enroll(enrollReq);
                }
                en.setCoursesRequired(en.getCoursesRequired() != null ? en.getCoursesRequired() + 1 : 1);
            } catch (Exception e) {
                log.warn("自动 enroll 课程失败: courseId={}, userId={}", mc.getCourseId(), en.getUserId(), e);
            }
        }

        // 更新 student_count（乐观锁）
        if (ms != null) {
            int msOldVersion = ms.getVersion();
            msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, ms.getId())
                            .eq(MicroSpecialty::getVersion, msOldVersion)
                            .setSql("student_count = COALESCE(student_count, 0) + 1")
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
        }

        // 通知学生
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_APPROVED,
                "报名已通过", "您的微专业报名已通过", en.getMicroSpecialtyId());

        en.setStatus("APPROVED");
        return toVO(en, ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

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
    @Transactional(rollbackFor = Exception.class)
    public int classImport(Long msId, Long classId) {
        MicroSpecialty ms = msRepository.selectById(msId);
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
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_INVITE_LEAD,
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
                notificationService.notifyAsync(au.getId(), NotificationType.MS_INVITE_LEAD,
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

        // student_count 防负
        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms != null && ms.getStudentCount() != null && ms.getStudentCount() > 0) {
            int msOldVersion = ms.getVersion();
            msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, ms.getId())
                            .eq(MicroSpecialty::getVersion, msOldVersion)
                            .setSql("student_count = student_count - 1")
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyEnrollmentVO reapply(Long id) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(id);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        // 仅 REJECTED/DROPPED/FAILED → PENDING（§2.2）
        String currentStatus = en.getStatus();
        if (!"REJECTED".equals(currentStatus) && !"DROPPED".equals(currentStatus) && !"FAILED".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已驳回/已退出/未通过状态可重新申请");
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

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());

        // 通知 LEAD
        if (ms != null && ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ENROLLMENT_REAPPLIED,
                    "学生重新申请微专业", "学生重新申请加入《" + ms.getTitle() + "》", en.getMicroSpecialtyId());
        }
        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_ENROLLMENT_REAPPLIED,
                "重新申请已提交", "您的微专业重新申请已提交，请等待审批", en.getMicroSpecialtyId());

        en.setStatus("PENDING");
        return toVO(en, ms);
    }

    @Override
    public List<MicroSpecialtyEnrollmentVO> getMyEnrollments() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<MicroSpecialtyEnrollment> list = enrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getUserId, userId)
                        .orderByDesc(MicroSpecialtyEnrollment::getCreatedAt));
        return list.stream().map(en -> {
            MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
            return toVO(en, ms);
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<MicroSpecialtyEnrollmentVO> listEnrollments(Long msId, int page, int size, String status) {
        LambdaQueryWrapper<MicroSpecialtyEnrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroSpecialtyEnrollment::getStatus, status);
        }
        wrapper.orderByDesc(MicroSpecialtyEnrollment::getCreatedAt);

        IPage<MicroSpecialtyEnrollment> ipage = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        MicroSpecialty ms = msRepository.selectById(msId);

        List<MicroSpecialtyEnrollmentVO> vos = ipage.getRecords().stream()
                .map(en -> toVO(en, ms)).collect(Collectors.toList());
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void issueCertificate(Long enrollmentId) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(enrollmentId);
        if (en == null) throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);

        if (!"COMPLETED".equals(en.getStatus())) {
            throw new BusinessException(ErrorCode.MS_CERT_NOT_READY);
        }

        // 幂等检查：已发过证书
        if (en.getCertificateId() != null) return;

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 生成证书编号：MS-{code}-{userId}-{yyyyMM}-{randomHex(4)}（§9.6）
        String yearMonth = String.format("%tY%<tm", LocalDateTime.now());
        String certCode = "MS-" + ms.getCode() + "-" + en.getUserId() + "-" + yearMonth + "-"
                + String.format("%04x", RANDOM.nextInt(0x10000));

        // 回写 enrollment
        int oldVersion = en.getVersion();
        enrollmentRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                        .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                        .set(MicroSpecialtyEnrollment::getStatus, "CERTIFIED")
                        .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));

        notificationService.notifyAsync(en.getUserId(), NotificationType.MS_CERTIFICATE_ISSUED,
                "微专业证书已颁发", "恭喜！您已获得微专业《" + ms.getTitle() + "》结业证书", en.getMicroSpecialtyId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateProgress(Long enrollmentId) {
        MicroSpecialtyEnrollment en = enrollmentRepository.selectById(enrollmentId);
        if (en == null) return;

        if (!"IN_PROGRESS".equals(en.getStatus()) && !"APPROVED".equals(en.getStatus())) return;

        MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
        if (ms == null) return;

        // 获取所有必修课
        List<MicroSpecialtyCourse> requiredCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, en.getMicroSpecialtyId())
                        .eq(MicroSpecialtyCourse::getIsRequired, true));

        int coursesCompleted = 0;
        double totalRequiredScore = 0;
        int requiredCount = 0;
        double totalElectiveScore = 0;
        int electivePassedCount = 0;
        BigDecimal creditsEarned = BigDecimal.ZERO;

        for (MicroSpecialtyCourse mc : requiredCourses) {
            Enrollment courseEn = courseEnrollmentRepository.selectOne(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getCourseId, mc.getCourseId())
                            .eq(Enrollment::getUserId, en.getUserId())
                            .ne(Enrollment::getEnrollmentStatus, "CANCELLED"));
            if (courseEn != null && courseEn.getFinalScore() != null) {
                BigDecimal minScore = mc.getMinScore() != null ? mc.getMinScore() : BigDecimal.valueOf(60);
                if (courseEn.getFinalScore().compareTo(minScore) >= 0) {
                    coursesCompleted++;
                    creditsEarned = creditsEarned.add(mc.getCredits() != null ? mc.getCredits() : BigDecimal.ZERO);
                }
                totalRequiredScore += courseEn.getFinalScore().doubleValue();
                requiredCount++;
            }
        }

        // 计算进度
        double progress = requiredCourses.isEmpty() ? 100.0
                : (double) coursesCompleted / requiredCourses.size() * 100.0;
        double avgRequired = requiredCount > 0 ? totalRequiredScore / requiredCount : 0;
        double avgElective = electivePassedCount > 0 ? totalElectiveScore / electivePassedCount : 0;
        double finalScore = avgRequired * 0.7 + avgElective * 0.3;

        // 判定完成（§6.8）
        boolean isCompleted = false;
        String rule = ms.getCompletionRule();
        if ("ALL_REQUIRED".equals(rule)) {
            isCompleted = coursesCompleted >= (ms.getRequiredCourseCount() != null ? ms.getRequiredCourseCount() : requiredCourses.size());
        } else if ("CREDITS_MIN".equals(rule)) {
            isCompleted = ms.getMinCredits() != null && creditsEarned.compareTo(ms.getMinCredits()) >= 0;
        } else if ("MIXED".equals(rule)) {
            boolean requiredOk = coursesCompleted >= (ms.getRequiredCourseCount() != null ? ms.getRequiredCourseCount() : requiredCourses.size());
            boolean creditsOk = ms.getMinCredits() != null && creditsEarned.compareTo(ms.getMinCredits()) >= 0;
            isCompleted = requiredOk && creditsOk;
        }

        int oldVersion = en.getVersion();

        if (isCompleted) {
            int affected = enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                            .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                            .set(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                            .set(MicroSpecialtyEnrollment::getProgress, BigDecimal.valueOf(progress))
                            .set(MicroSpecialtyEnrollment::getCreditsEarned, creditsEarned)
                            .set(MicroSpecialtyEnrollment::getCoursesCompleted, coursesCompleted)
                            .set(MicroSpecialtyEnrollment::getFinalScore, BigDecimal.valueOf(finalScore))
                            .set(MicroSpecialtyEnrollment::getCompletedAt, LocalDateTime.now())
                            .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (affected > 0) {
                // 自动颁发证书
                issueCertificate(enrollmentId);
            }
        } else {
            // 仅更新进度
            enrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, enrollmentId)
                            .eq(MicroSpecialtyEnrollment::getVersion, oldVersion)
                            .set(MicroSpecialtyEnrollment::getProgress, BigDecimal.valueOf(progress))
                            .set(MicroSpecialtyEnrollment::getCreditsEarned, creditsEarned)
                            .set(MicroSpecialtyEnrollment::getCoursesCompleted, coursesCompleted)
                            .set(MicroSpecialtyEnrollment::getFinalScore, BigDecimal.valueOf(finalScore))
                            .set(MicroSpecialtyEnrollment::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
        }
    }

    private MicroSpecialtyEnrollmentVO toVO(MicroSpecialtyEnrollment en, MicroSpecialty ms) {
        MicroSpecialtyEnrollmentVO vo = new MicroSpecialtyEnrollmentVO();
        vo.setId(en.getId());
        vo.setMicroSpecialtyId(en.getMicroSpecialtyId());
        vo.setUserId(en.getUserId());
        vo.setSource(en.getSource());
        vo.setClassId(en.getClassId());
        vo.setStatus(en.getStatus());
        vo.setProgress(en.getProgress());
        vo.setCreditsEarned(en.getCreditsEarned());
        vo.setCoursesCompleted(en.getCoursesCompleted());
        vo.setCoursesRequired(en.getCoursesRequired());
        vo.setFinalScore(en.getFinalScore());
        vo.setFinalGrade(en.getFinalGrade());
        vo.setCertificateId(en.getCertificateId());
        vo.setCanDownloadCert(en.getCertificateId() != null);
        vo.setPendingCourses(en.getPendingCourses());   // G2
        vo.setAppliedAt(en.getAppliedAt());
        vo.setApprovedAt(en.getApprovedAt());
        vo.setCompletedAt(en.getCompletedAt());
        vo.setDroppedAt(en.getDroppedAt());
        vo.setDropReason(en.getDropReason());

        if (ms != null) {
            vo.setMicroSpecialtyTitle(ms.getTitle());
            vo.setCoverUrl(ms.getCoverUrl());
        }
        if (en.getUserId() != null) {
            User user = userRepository.selectById(en.getUserId());
            if (user != null) vo.setUserName(user.getRealName());
        }
        return vo;
    }
}

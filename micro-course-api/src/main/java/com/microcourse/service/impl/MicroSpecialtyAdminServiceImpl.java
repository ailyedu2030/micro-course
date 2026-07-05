package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyFeaturedAudit;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.MicroSpecialtyStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyFeaturedAuditRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.MicroSpecialtyAdminService;
import com.microcourse.service.MicroSpecialtyQueryService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MicroSpecialtyAdminServiceImpl implements MicroSpecialtyAdminService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyAdminServiceImpl.class);

    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository;
    private final MicroSpecialtyQueryService queryService;

    public MicroSpecialtyAdminServiceImpl(MicroSpecialtyRepository msRepository,
                                          MicroSpecialtyCourseRepository msCourseRepository,
                                          MicroSpecialtyTeacherRepository msTeacherRepository,
                                          MicroSpecialtyEnrollmentRepository msEnrollmentRepository,
                                          UserRepository userRepository,
                                          EnrollmentRepository enrollmentRepository,
                                          NotificationService notificationService,
                                          MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository,
                                          MicroSpecialtyQueryService queryService) {
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.msEnrollmentRepository = msEnrollmentRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.msFeaturedAuditRepository = msFeaturedAuditRepository;
        this.queryService = queryService;
    }

    // ====== 角色鉴权（§9.12） ======

    @Override
    public void requireLeadOf(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (!queryService.isLeadOf(msId, userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.MS_FORBIDDEN, "您不是该微专业的LEAD，无权操作");
        }
    }

    @Override
    public void requireOwnerOrLead(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (!queryService.isOwnerOrLead(msId, userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.MS_FORBIDDEN, "无权操作此微专业");
        }
    }

    @Override
    public void checkNotTerminal(MicroSpecialty ms) {
        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == MicroSpecialtyStatus.CANCELLED || current == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }
    }

    // ====== 状态流转 ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(id);

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.PENDING_REVIEW)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅草稿或已驳回状态可提交审核");
        }

        // LEAD 必须已接受邀请
        Long leadCount = msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, id)
                        .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (leadCount == 0) throw new BusinessException(ErrorCode.MS_LEAD_REQUIRED);

        // 至少编排 1 门课程
        Long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id));
        if (courseCount < 1) throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "请至少编排一门课程后再提交");

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "PENDING_REVIEW")
                        .set(MicroSpecialty::getSubmittedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        List<User> academicUsers = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        for (User au : academicUsers) {
            notificationService.notifyAsync(au.getId(), NotificationType.MS_SUBMITTED,
                    "微专业待审核", "微专业《" + ms.getTitle() + "》已提交审核", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // P0-S05: 阻断自审批 — 微专业负责人不能审批自己的微专业
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (ms.getLeadTeacherId() != null) {
            SecurityUtil.assertNotSelf(currentUserId, ms.getLeadTeacherId(), "不能审批自己的微专业");
        }

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批通过");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "APPROVED")
                        .set(MicroSpecialty::getApprovedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_APPROVED,
                    "微专业已通过审核", "微专业《" + ms.getTitle() + "》已通过审核", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // P0-L03: 阻断自审批 — 微专业负责人不能驳回自己的微专业
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (ms.getLeadTeacherId() != null) {
            SecurityUtil.assertNotSelf(currentUserId, ms.getLeadTeacherId(), "不能驳回自己的微专业");
        }

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可驳回");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "REJECTED")
                        .set(MicroSpecialty::getRejectReason, com.microcourse.util.XssSanitizer.sanitizePlainText(reason))
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_REJECTED,
                    "微专业审核被驳回", "微专业《" + ms.getTitle() + "》被驳回，原因：" + (reason != null ? com.microcourse.util.XssSanitizer.sanitizePlainText(reason) : "未填写"), id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void open(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(id);

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.RECRUITING)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已通过状态可开课");
        }

        long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id));
        if (courseCount < 1) throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "课程编排未完成");

        Long teamCount = msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, id)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (teamCount < 2) throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "团队至少需要 2 名成员（含负责人）");

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "RECRUITING")
                        .set(MicroSpecialty::getOpenedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_OPENED,
                    "微专业已开课", "微专业《" + ms.getTitle() + "》已开放报名", id);
        }

        List<MicroSpecialtyEnrollment> enrolledStudents = msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, id)
                        .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS"));
        for (MicroSpecialtyEnrollment en : enrolledStudents) {
            notificationService.notifyAsync(en.getUserId(), NotificationType.MS_OPENED,
                    "微专业已开课", "您修读的微专业《" + ms.getTitle() + "》已开放报名", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(id);

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可结业");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "COMPLETED")
                        .set(MicroSpecialty::getClosedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        List<User> academicUsersClose = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        for (User au : academicUsersClose) {
            notificationService.notifyAsync(au.getId(), NotificationType.MS_COMPLETED,
                    "微专业已结业", "微专业《" + ms.getTitle() + "》已结业", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "取消原因不能为空");
        }
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // ★ P1-I-4 修复：Service 层只允许 ADMIN/ACADEMIC 取消微专业
        if (!SecurityUtil.isAdmin() && !SecurityUtil.hasRole("ACADEMIC")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // ★ P1-I-3 修复：已结业不可取消，已归档/已取消不可再取消
        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == MicroSpecialtyStatus.CANCELLED || current == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "已取消/已归档微专业不能再取消");
        }
        if (current == MicroSpecialtyStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "已结业微专业应先归档，不能直接取消");
        }
        // P1-I-1 修复：枚举 canTransitionTo 检查
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许取消");
        }

        String safeReason = com.microcourse.util.XssSanitizer.sanitizePlainText(reason);

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "CANCELLED")
                        .set(MicroSpecialty::getCancelReason, safeReason)
                        .set(MicroSpecialty::getClosedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // 级联：所有 PENDING/APPROVED/IN_PROGRESS enrollment → DROPPED（§9.8）
        List<MicroSpecialtyEnrollment> enrollments = msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, id)
                        .in(MicroSpecialtyEnrollment::getStatus, "PENDING", "APPROVED", "IN_PROGRESS"));
        for (MicroSpecialtyEnrollment en : enrollments) {
            int enAffected = msEnrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, en.getId())
                            .eq(MicroSpecialtyEnrollment::getVersion, en.getVersion())
                            .set(MicroSpecialtyEnrollment::getStatus, "DROPPED")
                            .set(MicroSpecialtyEnrollment::getDropReason, "SPECIALTY_CANCELLED")
                            .set(MicroSpecialtyEnrollment::getDroppedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (enAffected == 0) {
                log.warn("cancel() 级联 DROPPED 跳过: enrollment.id={} 已被其他操作修改", en.getId());
            }
        }

        // 课程级 enrollment 级联清理
        List<MicroSpecialtyCourse> msCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id));
        for (MicroSpecialtyEnrollment en : enrollments) {
            for (MicroSpecialtyCourse mc : msCourses) {
                Enrollment courseEn = enrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.COMPLETED.getValue()));
                if (courseEn != null && !EnrollmentStatus.CANCELLED.getValue().equals(courseEn.getEnrollmentStatus())) {
                    enrollmentRepository.update(null,
                            new LambdaUpdateWrapper<Enrollment>()
                                    .eq(Enrollment::getId, courseEn.getId())
                                    .eq(Enrollment::getVersion, courseEn.getVersion())
                                    .set(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue())
                                    .set(Enrollment::getUpdatedAt, LocalDateTime.now())
                                    .setSql("version = version + 1"));
                }
            }
        }

        // 写入审计日志
        try {
            MicroSpecialtyFeaturedAudit audit = new MicroSpecialtyFeaturedAudit();
            audit.setMicroSpecialtyId(id);
            audit.setOperatorId(SecurityUtil.getCurrentUserId());
            audit.setAction("CANCELLED");
            audit.setBeforeValue(new ObjectMapper().writeValueAsString(ms));
            audit.setAfterValue(null);
            audit.setReason(safeReason);
            audit.setCreatedAt(LocalDateTime.now());
            msFeaturedAuditRepository.insert(audit);
        } catch (JsonProcessingException e) {
            log.warn("写入取消审计日志时 JSON 序列化失败: msId={}", id, e);
        }

        // 通知 LEAD（附带原因）
        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_CANCELLED,
                    "微专业已取消", "微专业《" + ms.getTitle() + "》已被取消，原因：" + safeReason, id);
        }
        // 通知所有受影响学生
        for (MicroSpecialtyEnrollment en : enrollments) {
            notificationService.notifyAsync(en.getUserId(), NotificationType.MS_CANCELLED,
                    "微专业已取消", "您修读的微专业《" + ms.getTitle() + "》已被取消，原因：" + safeReason, id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        MicroSpecialtyStatus current = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (current == null || !current.canTransitionTo(MicroSpecialtyStatus.ARCHIVED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已完成状态可归档");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, ms.getStatus())
                        .set(MicroSpecialty::getStatus, "ARCHIVED")
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ARCHIVED,
                    "微专业已归档", "微专业《" + ms.getTitle() + "》已归档", id);
        }
    }

    // ====== LEAD 继任（§9.7） ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        MicroSpecialtyStatus s = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (s == MicroSpecialtyStatus.CANCELLED || s == MicroSpecialtyStatus.COMPLETED || s == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "终态微专业不可转移负责人");
        }

        Long newLeadId = request.getNewLeadTeacherId();
        User newLead = userRepository.selectById(newLeadId);
        if (newLead == null) throw new BusinessException(ErrorCode.MS_LEAD_TRANSFER_INVALID);

        int oldVersion = ms.getVersion();

        // 1. 原 LEAD 降为 MEMBER（乐观锁）
        MicroSpecialtyTeacher oldLead = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (oldLead != null) {
            int oldLeadVersion = oldLead.getVersion() != null ? oldLead.getVersion() : 0;
            int affected1 = msTeacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, oldLead.getId())
                            .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                            .eq(MicroSpecialtyTeacher::getVersion, oldLeadVersion)
                            .set(MicroSpecialtyTeacher::getRole, "MEMBER")
                            .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (affected1 == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        // 2. 新 LEAD 是否已在团队（乐观锁）
        MicroSpecialtyTeacher newLeadRecord = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, newLeadId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (newLeadRecord != null) {
            int newLeadVersion = newLeadRecord.getVersion() != null ? newLeadRecord.getVersion() : 0;
            int affected2 = msTeacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, newLeadRecord.getId())
                            .eq(MicroSpecialtyTeacher::getVersion, newLeadVersion)
                            .set(MicroSpecialtyTeacher::getRole, "LEAD")
                            .set(MicroSpecialtyTeacher::getJoinedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (affected2 == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        } else {
            newLeadRecord = new MicroSpecialtyTeacher();
            newLeadRecord.setMicroSpecialtyId(msId);
            newLeadRecord.setTeacherId(newLeadId);
            newLeadRecord.setRole("LEAD");
            newLeadRecord.setInviteStatus("ACTIVE");
            newLeadRecord.setJoinedAt(LocalDateTime.now());
            newLeadRecord.setCreatedAt(LocalDateTime.now());
            msTeacherRepository.insert(newLeadRecord);
        }

        // 3. 更新主表 lead_teacher_id
        int affected3 = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getLeadTeacherId, newLeadId)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected3 == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // 4. 通知
        if (oldLead != null) {
            notificationService.notifyAsync(oldLead.getTeacherId(), NotificationType.MS_LEAD_TRANSFERRED,
                    "负责人已变更", "微专业《" + ms.getTitle() + "》负责人已变更", msId);
        }
        notificationService.notifyAsync(newLeadId, NotificationType.MS_LEAD_TRANSFERRED,
                "您已成为微专业负责人", "您已被指定为微专业《" + ms.getTitle() + "》新负责人", msId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchApprove(List<Long> ids) {
        BatchOperationResult result = new BatchOperationResult();
        for (Long id : ids) {
            try {
                approve(id);
                result.addSuccess(id);
            } catch (Exception e) {
                log.warn("批量微专业审批通过失败, id={}, reason={}", id, e.getMessage());
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchReject(List<Long> ids, String reason) {
        BatchOperationResult result = new BatchOperationResult();
        for (Long id : ids) {
            try {
                reject(id, reason);
                result.addSuccess(id);
            } catch (Exception e) {
                log.warn("批量微专业审批驳回失败, id={}, reason={}", id, e.getMessage());
                result.addFailure(id, e.getMessage());
            }
        }
        return result;
    }
}

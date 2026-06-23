package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.*;
import com.microcourse.entity.*;
import com.microcourse.enums.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.*;
import com.microcourse.util.SecurityUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MicroSpecialtyServiceImpl implements MicroSpecialtyService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyServiceImpl.class);

    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    private final MicroSpecialtyProposalRepository msProposalRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final NotificationService notificationService;
    private final MicroSpecialtyEnrollmentService msEnrollmentService;
    private final MicroSpecialtyQualityScoreService qualityScoreService;
    private final AdminSettingService adminSettingService;
    private final MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository;
    private final MicroSpecialtyFeaturedService featuredService;

    public MicroSpecialtyServiceImpl(MicroSpecialtyRepository msRepository,
                                     MicroSpecialtyCourseRepository msCourseRepository,
                                     MicroSpecialtyTeacherRepository msTeacherRepository,
                                     MicroSpecialtyEnrollmentRepository msEnrollmentRepository,
                                     MicroSpecialtyProposalRepository msProposalRepository,
                                     UserRepository userRepository,
                                     EnrollmentRepository enrollmentRepository,
                                     NotificationService notificationService,
                                     @Lazy MicroSpecialtyEnrollmentService msEnrollmentService,
                                     MicroSpecialtyQualityScoreService qualityScoreService,
                                     AdminSettingService adminSettingService,
                                     MicroSpecialtyFeaturedAuditRepository msFeaturedAuditRepository,
                                     MicroSpecialtyFeaturedService featuredService) {
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.msEnrollmentRepository = msEnrollmentRepository;
        this.msProposalRepository = msProposalRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.notificationService = notificationService;
        this.msEnrollmentService = msEnrollmentService;
        this.qualityScoreService = qualityScoreService;
        this.adminSettingService = adminSettingService;
        this.msFeaturedAuditRepository = msFeaturedAuditRepository;
        this.featuredService = featuredService;
    }

    // ====== 查询 ======

    @Override
    public PageResult<MicroSpecialtyVO> page(int page, int size, String keyword, String status) {
        LambdaQueryWrapper<MicroSpecialty> wrapper = new LambdaQueryWrapper<>();

        // 学生只能看 RECRUITING 状态的微专业
        if (!SecurityUtil.isAdminOrAcademic()) {
            wrapper.eq(MicroSpecialty::getStatus, "RECRUITING");
        } else if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroSpecialty::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MicroSpecialty::getTitle, keyword);
        }
        wrapper.orderByDesc(MicroSpecialty::getCreatedAt);

        IPage<MicroSpecialty> ipage = msRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<MicroSpecialtyVO> vos = ipage.getRecords().stream()
                .map(this::toVO).collect(Collectors.toList());
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    @Override
    public MicroSpecialtySquareVO getSquareData() {
        MicroSpecialtySquareVO result = new MicroSpecialtySquareVO();

        // 金标（最多 2 个）
        List<MicroSpecialty> goldList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getIsGoldFeatured, true)
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .orderByDesc(MicroSpecialty::getGoldFeaturedAt)
                        .last("LIMIT 2"));
        result.setGoldFeatured(goldList.stream().map(this::toFeaturedVO).collect(Collectors.toList()));

        // 置顶已审批
        List<MicroSpecialty> featuredList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getIsFeatured, true)
                        .eq(MicroSpecialty::getFeaturedStatus, "APPROVED")
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .orderByAsc(MicroSpecialty::getFeaturedRank)
                        .orderByDesc(MicroSpecialty::getApprovedAt));
        result.setFeatured(featuredList.stream().map(this::toFeaturedVO).collect(Collectors.toList()));

        // 普通招生中（排除已置顶的）— 修复 G1：按质量分降序排序（次按 approvedAt DESC）
        List<MicroSpecialty> recruitingList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .and(w -> w.eq(MicroSpecialty::getIsFeatured, false)
                                .or().isNull(MicroSpecialty::getIsFeatured))
                        .and(w -> w.eq(MicroSpecialty::getIsGoldFeatured, false)
                                .or().isNull(MicroSpecialty::getIsGoldFeatured))
                        .orderByDesc(MicroSpecialty::getApprovedAt));
        // G1: 用质量分降序重排（次按 approvedAt DESC）
        List<Long> recruitingIds = recruitingList.stream().map(MicroSpecialty::getId).collect(Collectors.toList());
        Map<Long, BigDecimal> scoreMap = qualityScoreService.calculateBatch(recruitingIds);
        recruitingList = recruitingList.stream()
                .sorted((a, b) -> {
                    BigDecimal sa = scoreMap.getOrDefault(a.getId(), BigDecimal.ZERO);
                    BigDecimal sb = scoreMap.getOrDefault(b.getId(), BigDecimal.ZERO);
                    int cmp = sb.compareTo(sa); // 质量分 DESC
                    if (cmp != 0) return cmp;
                    // 次按 approvedAt DESC
                    LocalDateTime ta = a.getApprovedAt();
                    LocalDateTime tb = b.getApprovedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());
        result.setRecruiting(recruitingList.stream().map(this::toFeaturedVO).collect(Collectors.toList()));

        return result;
    }

    private MicroSpecialtySquareVO.FeaturedVO toFeaturedVO(MicroSpecialty ms) {
        MicroSpecialtySquareVO.FeaturedVO vo = new MicroSpecialtySquareVO.FeaturedVO();
        vo.setId(ms.getId());
        vo.setTitle(ms.getTitle());
        vo.setCoverUrl(ms.getCoverUrl());
        vo.setTotalCredits(ms.getTotalCredits());
        vo.setStudentCount(ms.getStudentCount());
        vo.setStatus(ms.getStatus());
        vo.setIsGoldFeatured(ms.getIsGoldFeatured());
        // G1: 设置质量分
        vo.setQualityScore(qualityScoreService.calculate(ms.getId()));
        // G3: 7 天保护期内显示 NEW 角标
        vo.setIsNew(isNewlyCreated(ms));
        if (ms.getLeadTeacherId() != null) {
            User lead = userRepository.selectById(ms.getLeadTeacherId());
            if (lead != null) vo.setLeadTeacherName(lead.getRealName());
        }
        return vo;
    }

    /**
     * G3: 7 天保护期判断
     * 读取 admin_settings 中微专业 NEW 角标保护期配置（默认 7 天，可配置）
     */
    private boolean isNewlyCreated(MicroSpecialty ms) {
        if (ms.getApprovedAt() == null) return false;
        try {
            int protectionDays = 7;
            try {
                String configVal = adminSettingService.getByKey("micro_specialty.new_protection_days");
                if (configVal != null && !configVal.isEmpty()) {
                    protectionDays = Integer.parseInt(configVal);
                }
            } catch (Exception ignore) {
                // AdminSetting 不存在或字段缺失，保留默认 7
            }
            return ms.getApprovedAt().plusDays(protectionDays).isAfter(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MicroSpecialtyDetailVO getDetail(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        MicroSpecialtyDetailVO detail = new MicroSpecialtyDetailVO();
        copyToVO(ms, detail);

        // 预加载部门名、教师名
        if (ms.getOfferDepartmentId() != null) {
            User deptUser = userRepository.selectById(ms.getOfferDepartmentId());
            // department name lookup via Department entity would be ideal; fallback to deptId as-is
        }
        if (ms.getLeadTeacherId() != null) {
            User lead = userRepository.selectById(ms.getLeadTeacherId());
            if (lead != null) detail.setLeadTeacherName(lead.getRealName());
        }

        // 课程编排
        List<MicroSpecialtyCourse> courses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id)
                        .orderByAsc(MicroSpecialtyCourse::getSortOrder));
        detail.setCourses(courses.stream().map(this::toCourseVO).collect(Collectors.toList()));

        // 教师团队
        List<MicroSpecialtyTeacher> teachers = msTeacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, id)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        detail.setTeachers(teachers.stream().map(this::toTeacherVO).collect(Collectors.toList()));

        // 统计
        detail.setStats(buildStats(ms));

        return detail;
    }

    @Override
    public MicroSpecialtyStatsVO stats(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        return buildStats(ms);
    }

    private MicroSpecialtyStatsVO buildStats(MicroSpecialty ms) {
        MicroSpecialtyStatsVO vo = new MicroSpecialtyStatsVO();

        long totalCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId()));
        long completedCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED"));
        long inProgress = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "IN_PROGRESS"));
        long failed = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "FAILED"));

        vo.setTotalEnrollments((int) totalCount);
        vo.setCompletedCount((int) completedCount);
        vo.setInProgressCount((int) inProgress);
        vo.setFailedCount((int) failed);

        long activeTotal = completedCount + inProgress + failed;
        if (ms.getMaxStudents() != null && ms.getMaxStudents() > 0) {
            vo.setEnrollmentRate(BigDecimal.valueOf(Math.min(ms.getStudentCount(), ms.getMaxStudents()))
                    .divide(BigDecimal.valueOf(ms.getMaxStudents()), 4, java.math.RoundingMode.HALF_UP));
        }
        vo.setCompletionRate(activeTotal > 0
                ? BigDecimal.valueOf(completedCount).divide(BigDecimal.valueOf(activeTotal), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return vo;
    }

    // ====== CUD ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyVO create(MicroSpecialtyCreateRequest request) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setCode(request.getCode());
        ms.setTitle(request.getTitle());
        ms.setSubtitle(request.getSubtitle());
        ms.setCoverUrl(request.getCoverUrl());
        ms.setDescription(request.getDescription());
        ms.setOfferDepartmentId(request.getOfferDepartmentId());
        ms.setLeadTeacherId(request.getLeadTeacherId());
        ms.setTargetAudience(request.getTargetAudience());
        ms.setTrainingObjective(request.getTrainingObjective());
        ms.setAdmissionRequirement(request.getAdmissionRequirement());
        ms.setCompletionRule(request.getCompletionRule());
        ms.setSemester(request.getSemester());
        ms.setMaxStudents(request.getMaxStudents());
        ms.setStatus("DRAFT");
        ms.setCreatorId(SecurityUtil.getCurrentUserId());
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        ms.setVersion(0);
        ms.setIsFeatured(false);
        ms.setIsGoldFeatured(false);
        ms.setFeaturedStatus("NONE");
        ms.setStudentCount(0);

        msRepository.insert(ms);

        // 自动创建 LEAD INVITED 记录
        MicroSpecialtyTeacher leadRecord = new MicroSpecialtyTeacher();
        leadRecord.setMicroSpecialtyId(ms.getId());
        leadRecord.setTeacherId(request.getLeadTeacherId());
        leadRecord.setRole("LEAD");
        leadRecord.setInviteStatus("INVITED");
        leadRecord.setInvitedBy(SecurityUtil.getCurrentUserId());
        leadRecord.setInvitedAt(LocalDateTime.now());
        leadRecord.setInviteExpiresAt(LocalDateTime.now().plusDays(7));
        leadRecord.setCreatedAt(LocalDateTime.now());
        msTeacherRepository.insert(leadRecord);

        // 通知 LEAD
        if (request.getLeadTeacherId() != null) {
            notificationService.notifyAsync(request.getLeadTeacherId(), NotificationType.MS_INVITE_LEAD,
                    "微专业负责人邀请", "您已被指定为微专业《" + ms.getTitle() + "》负责人，请在7天内接受邀请", ms.getId());
        }

        return toVO(ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyVO update(Long id, MicroSpecialtyUpdateRequest request) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        int oldVersion = ms.getVersion();

        // 状态编辑范围校验（§9.11）
        String status = ms.getStatus();
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许编辑");
        }

        if (request.getTitle() != null) ms.setTitle(request.getTitle());
        if (request.getSubtitle() != null) ms.setSubtitle(request.getSubtitle());
        if (request.getCoverUrl() != null) ms.setCoverUrl(request.getCoverUrl());
        if (request.getDescription() != null) ms.setDescription(request.getDescription());
        if (request.getTargetAudience() != null) ms.setTargetAudience(request.getTargetAudience());
        if (request.getTrainingObjective() != null) ms.setTrainingObjective(request.getTrainingObjective());
        if (request.getAdmissionRequirement() != null) ms.setAdmissionRequirement(request.getAdmissionRequirement());
        if (request.getCompletionRule() != null) ms.setCompletionRule(request.getCompletionRule());
        if (request.getSemester() != null) ms.setSemester(request.getSemester());
        if (request.getMaxStudents() != null) ms.setMaxStudents(request.getMaxStudents());
        ms.setUpdatedAt(LocalDateTime.now());
        ms.setVersion(oldVersion + 1);

        int affected = msRepository.updateById(ms);
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        return toVO(ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 仅 DRAFT/REJECTED/ARCHIVED 可删除
        String s = ms.getStatus();
        if (!"DRAFT".equals(s) && !"REJECTED".equals(s) && !"ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许删除");
        }
        msRepository.deleteById(id);
    }

    // ====== 状态流转 ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        String currentStatus = ms.getStatus();
        // §2.1: DRAFT/REJECTED → PENDING_REVIEW
        if (!"DRAFT".equals(currentStatus) && !"REJECTED".equals(currentStatus)) {
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
                        .eq(MicroSpecialty::getStatus, currentStatus)
                        .set(MicroSpecialty::getStatus, "PENDING_REVIEW")
                        .set(MicroSpecialty::getSubmittedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // P0-2 修复：通知所有 ACADEMIC 角色用户（而非非法 userId 0L）
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

        if (!"PENDING_REVIEW".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批通过");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, "PENDING_REVIEW")
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

        if (!"PENDING_REVIEW".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可驳回");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, "PENDING_REVIEW")
                        .set(MicroSpecialty::getStatus, "REJECTED")
                        .set(MicroSpecialty::getRejectReason, reason)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_REJECTED,
                    "微专业审核被驳回", "微专业《" + ms.getTitle() + "》被驳回，原因：" + (reason != null ? reason : "未填写"), id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void open(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"APPROVED".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已通过状态可开课");
        }

        // 课程编排完成（≥1门）
        long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id));
        if (courseCount < 1) throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "课程编排未完成");

        // 团队至少 2 名成员（含 LEAD）
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
                        .eq(MicroSpecialty::getStatus, "APPROVED")
                        .set(MicroSpecialty::getStatus, "RECRUITING")
                        .set(MicroSpecialty::getOpenedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_OPENED,
                    "微专业已开课", "微专业《" + ms.getTitle() + "》已开放报名", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"RECRUITING".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可结业");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .set(MicroSpecialty::getStatus, "COMPLETED")
                        .set(MicroSpecialty::getClosedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        // P0-2 修复：通知所有 ACADEMIC 角色用户（而非非法 userId 0L）
        List<User> academicUsersClose = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        for (User au : academicUsersClose) {
            notificationService.notifyAsync(au.getId(), NotificationType.MS_COMPLETED,
                    "微专业已结业", "微专业《" + ms.getTitle() + "》已结业", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 禁止重复取消
        if ("CANCELLED".equals(ms.getStatus())) throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业已被取消");

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getStatus, "CANCELLED")
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
            msEnrollmentRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                            .eq(MicroSpecialtyEnrollment::getId, en.getId())
                            .eq(MicroSpecialtyEnrollment::getVersion, en.getVersion())
                            .set(MicroSpecialtyEnrollment::getStatus, "DROPPED")
                            .set(MicroSpecialtyEnrollment::getDropReason, "SPECIALTY_CANCELLED")
                            .set(MicroSpecialtyEnrollment::getDroppedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
        }

        // 课程级 enrollment 级联清理：对于 COMPLETED 以外的课程级 enrollment 做软删除
        List<MicroSpecialtyCourse> msCourses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id));
        for (MicroSpecialtyEnrollment en : enrollments) {
            for (MicroSpecialtyCourse mc : msCourses) {
                Enrollment courseEn = enrollmentRepository.selectOne(
                        new LambdaQueryWrapper<Enrollment>()
                                .eq(Enrollment::getCourseId, mc.getCourseId())
                                .eq(Enrollment::getUserId, en.getUserId())
                                .ne(Enrollment::getEnrollmentStatus, "COMPLETED"));
                if (courseEn != null) {
                    enrollmentRepository.deleteById(courseEn.getId());
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
            audit.setReason("微专业被取消");
            audit.setCreatedAt(LocalDateTime.now());
            msFeaturedAuditRepository.insert(audit);
        } catch (JsonProcessingException e) {
            log.warn("写入取消审计日志时 JSON 序列化失败: msId={}", id, e);
        }

        // 通知 LEAD
        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_CANCELLED,
                    "微专业已取消", "微专业《" + ms.getTitle() + "》已被取消", id);
        }
        // 通知所有受影响学生
        for (MicroSpecialtyEnrollment en : enrollments) {
            notificationService.notifyAsync(en.getUserId(), NotificationType.MS_CANCELLED,
                    "微专业已取消", "您修读的微专业《" + ms.getTitle() + "》已被取消", id);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (!"COMPLETED".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已完成状态可归档");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getStatus, "COMPLETED")
                        .set(MicroSpecialty::getStatus, "ARCHIVED")
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_ARCHIVED,
                    "微专业已归档", "微专业《" + ms.getTitle() + "》已归档", id);
        }
    }

    // ====== 课程编排 ======

    @Override
    public List<MicroSpecialtyCourseVO> listCourses(Long msId) {
        return msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId)
                        .orderByAsc(MicroSpecialtyCourse::getSortOrder))
                .stream().map(this::toCourseVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyCourseVO addCourse(Long msId, MicroSpecialtyCourseRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // §9.11 编辑范围：RECRUITING 后不允许添加课程（仅可排序）
        String s = ms.getStatus();
        if ("RECRUITING".equals(s) || "COMPLETED".equals(s) || "CANCELLED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许添加课程");
        }

        MicroSpecialtyCourse item = new MicroSpecialtyCourse();
        item.setMicroSpecialtyId(msId);
        item.setCourseId(request.getCourseId());
        item.setSortOrder(request.getSortOrder());
        item.setIsRequired(request.getIsRequired() != null ? request.getIsRequired() : true);
        item.setCredits(request.getCredits());
        item.setHours(request.getHours());
        item.setMinScore(request.getMinScore());
        item.setRecommendedSemester(request.getRecommendedSemester());
        item.setCreatedAt(LocalDateTime.now());
        msCourseRepository.insert(item);
        return toCourseVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyCourseVO updateCourseItem(Long msId, Long itemId, MicroSpecialtyCourseRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // §9.11 编辑范围校验
        String s = ms.getStatus();
        if ("RECRUITING".equals(s) || "COMPLETED".equals(s) || "CANCELLED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许修改课程");
        }

        MicroSpecialtyCourse item = msCourseRepository.selectById(itemId);
        if (item == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        if (request.getSortOrder() != null) item.setSortOrder(request.getSortOrder());
        if (request.getIsRequired() != null) item.setIsRequired(request.getIsRequired());
        if (request.getCredits() != null) item.setCredits(request.getCredits());
        if (request.getHours() != null) item.setHours(request.getHours());
        if (request.getMinScore() != null) item.setMinScore(request.getMinScore());
        if (request.getRecommendedSemester() != null) item.setRecommendedSemester(request.getRecommendedSemester());
        msCourseRepository.updateById(item);
        return toCourseVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourse(Long msId, Long itemId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // §9.11 编辑范围校验
        String s = ms.getStatus();
        if ("RECRUITING".equals(s) || "COMPLETED".equals(s) || "CANCELLED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许移除课程");
        }

        msCourseRepository.deleteById(itemId);
    }

    // ====== 教师团队 ======

    @Override
    public List<MicroSpecialtyTeacherVO> listTeachers(Long msId) {
        List<MicroSpecialtyTeacher> teachers = msTeacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .notIn(MicroSpecialtyTeacher::getInviteStatus, "DECLINED", "REMOVED"));
        return teachers.stream().map(this::toTeacherVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 检查是否已存在有效记录
        Long existCount = msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, request.getTeacherId())
                        .notIn(MicroSpecialtyTeacher::getInviteStatus, "DECLINED", "REMOVED"));
        if (existCount > 0) throw new BusinessException(ErrorCode.MS_DUPLICATE_TEACHER);

        MicroSpecialtyTeacher record = new MicroSpecialtyTeacher();
        record.setMicroSpecialtyId(msId);
        record.setTeacherId(request.getTeacherId());
        record.setRole(request.getRole());
        record.setCourseId(request.getCourseId());
        record.setResponsibility(request.getResponsibility());
        record.setInviteStatus("INVITED");
        record.setInvitedBy(SecurityUtil.getCurrentUserId());
        record.setInvitedAt(LocalDateTime.now());
        record.setInviteExpiresAt(LocalDateTime.now().plusDays(7));
        record.setCreatedAt(LocalDateTime.now());
        msTeacherRepository.insert(record);

        notificationService.notifyAsync(request.getTeacherId(), NotificationType.MS_INVITE_TEAM,
                "微专业团队邀请", "您被邀请加入微专业《" + ms.getTitle() + "》团队", msId);

        return toTeacherVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTeacher(Long msId, Long teacherId) {
        Long existCount = msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, teacherId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (existCount == 0) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        msTeacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, teacherId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")
                        .set(MicroSpecialtyTeacher::getInviteStatus, "REMOVED")
                        .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now()));

        notificationService.notifyAsync(teacherId, NotificationType.MS_TEAM_REMOVED,
                "已被移出微专业团队", "您已被移出微专业团队", msId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyTeacherVO reinviteTeacher(Long msId, Long teacherId) {
        // 查找 REMOVED/DECLINED 记录复用
        MicroSpecialtyTeacher record = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, teacherId)
                        .in(MicroSpecialtyTeacher::getInviteStatus, "DECLINED", "REMOVED")
                        .last("LIMIT 1"));

        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND, "无可复用的教师记录");

        int oldVersion = 0;
        msTeacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getId, record.getId())
                        .set(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                        .set(MicroSpecialtyTeacher::getInvitedAt, LocalDateTime.now())
                        .set(MicroSpecialtyTeacher::getInviteExpiresAt, LocalDateTime.now().plusDays(7))
                        .set(MicroSpecialtyTeacher::getInvitedBy, SecurityUtil.getCurrentUserId()));

        return toTeacherVO(record);
    }

    // ====== LEAD 继任（§9.7） ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 仅非终态可转移
        String s = ms.getStatus();
        if ("CANCELLED".equals(s) || "COMPLETED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "终态微专业不可转移负责人");
        }

        Long newLeadId = request.getNewLeadTeacherId();
        User newLead = userRepository.selectById(newLeadId);
        if (newLead == null) throw new BusinessException(ErrorCode.MS_LEAD_TRANSFER_INVALID);

        int oldVersion = ms.getVersion();

        // 1. 原 LEAD 降为 MEMBER
        MicroSpecialtyTeacher oldLead = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (oldLead != null) {
            msTeacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, oldLead.getId())
                            .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                            .set(MicroSpecialtyTeacher::getRole, "MEMBER")
                            .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now()));
        }

        // 2. 新 LEAD 是否已在团队
        MicroSpecialtyTeacher newLeadRecord = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, newLeadId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (newLeadRecord != null) {
            msTeacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, newLeadRecord.getId())
                            .set(MicroSpecialtyTeacher::getRole, "LEAD")
                            .set(MicroSpecialtyTeacher::getJoinedAt, LocalDateTime.now()));
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
        msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getLeadTeacherId, newLeadId)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));

        // 4. 通知
        if (oldLead != null) {
            notificationService.notifyAsync(oldLead.getTeacherId(), NotificationType.MS_LEAD_TRANSFERRED,
                    "负责人已变更", "微专业《" + ms.getTitle() + "》负责人已变更", msId);
        }
        notificationService.notifyAsync(newLeadId, NotificationType.MS_LEAD_TRANSFERRED,
                "您已成为微专业负责人", "您已被指定为微专业《" + ms.getTitle() + "》新负责人", msId);
    }

    // ====== 置顶（委托 FeaturedService） ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyFeatured(Long msId, String reason) {
        featuredService.applyFeatured(msId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveFeatured(Long msId) {
        featuredService.approveFeatured(msId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectFeatured(Long msId, String reason) {
        featuredService.rejectFeatured(msId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unsetFeatured(Long msId) {
        featuredService.unsetFeatured(msId);
    }

    // ====== 角色鉴权（§9.12） ======

    @Override
    public boolean isLeadOf(Long msId, Long userId) {
        return msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")) > 0;
    }

    @Override
    public boolean isMemberOf(Long msId, Long userId) {
        return msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .in(MicroSpecialtyTeacher::getRole, "MEMBER", "ASSISTANT")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")) > 0;
    }

    @Override
    public boolean isOwnerOrLead(Long msId, Long userId) {
        if (isLeadOf(msId, userId)) return true;
        MicroSpecialty ms = msRepository.selectById(msId);
        return ms != null && userId.equals(ms.getCreatorId());
    }

    // ====== 转换方法 ======

    private MicroSpecialtyVO toVO(MicroSpecialty ms) {
        MicroSpecialtyVO vo = new MicroSpecialtyVO();
        copyToVO(ms, vo);
        return vo;
    }

    private void copyToVO(MicroSpecialty ms, MicroSpecialtyVO vo) {
        vo.setId(ms.getId());
        vo.setCode(ms.getCode());
        vo.setTitle(ms.getTitle());
        vo.setSubtitle(ms.getSubtitle());
        vo.setCoverUrl(ms.getCoverUrl());
        vo.setDescription(ms.getDescription());
        vo.setOfferDepartmentId(ms.getOfferDepartmentId());
        vo.setLeadTeacherId(ms.getLeadTeacherId());
        vo.setTargetAudience(ms.getTargetAudience());
        vo.setTrainingObjective(ms.getTrainingObjective());
        vo.setAdmissionRequirement(ms.getAdmissionRequirement());
        vo.setCompletionRule(ms.getCompletionRule());
        vo.setTotalCredits(ms.getTotalCredits());
        vo.setTotalHours(ms.getTotalHours());
        vo.setRequiredCourseCount(ms.getRequiredCourseCount());
        vo.setElectiveCourseCount(ms.getElectiveCourseCount());
        vo.setMinCredits(ms.getMinCredits());
        vo.setMaxStudents(ms.getMaxStudents());
        vo.setStudentCount(ms.getStudentCount());
        vo.setSemester(ms.getSemester());
        vo.setIsFeatured(ms.getIsFeatured());
        vo.setFeaturedRank(ms.getFeaturedRank());
        vo.setFeaturedStatus(ms.getFeaturedStatus());
        vo.setIsGoldFeatured(ms.getIsGoldFeatured());
        vo.setStatus(ms.getStatus());
        vo.setRejectReason(ms.getRejectReason());
        vo.setSubmittedAt(ms.getSubmittedAt());
        vo.setApprovedAt(ms.getApprovedAt());
        vo.setOpenedAt(ms.getOpenedAt());
        vo.setClosedAt(ms.getClosedAt());
        vo.setCreatorId(ms.getCreatorId());
        vo.setCreatedAt(ms.getCreatedAt());
    }

    private MicroSpecialtyCourseVO toCourseVO(MicroSpecialtyCourse c) {
        MicroSpecialtyCourseVO vo = new MicroSpecialtyCourseVO();
        vo.setId(c.getId());
        vo.setMicroSpecialtyId(c.getMicroSpecialtyId());
        vo.setCourseId(c.getCourseId());
        vo.setSortOrder(c.getSortOrder());
        vo.setIsRequired(c.getIsRequired());
        vo.setCredits(c.getCredits());
        vo.setHours(c.getHours());
        vo.setMinScore(c.getMinScore());
        vo.setRecommendedSemester(c.getRecommendedSemester());
        return vo;
    }

    private MicroSpecialtyTeacherVO toTeacherVO(MicroSpecialtyTeacher t) {
        MicroSpecialtyTeacherVO vo = new MicroSpecialtyTeacherVO();
        vo.setId(t.getId());
        vo.setMicroSpecialtyId(t.getMicroSpecialtyId());
        vo.setTeacherId(t.getTeacherId());
        vo.setRoleLabel(t.getRole());
        vo.setCourseId(t.getCourseId());
        vo.setResponsibility(t.getResponsibility());
        vo.setInviteStatus(t.getInviteStatus());
        vo.setInviteExpiresAt(t.getInviteExpiresAt());
        if (t.getTeacherId() != null) {
            User u = userRepository.selectById(t.getTeacherId());
            if (u != null) {
                vo.setTeacherName(u.getRealName());
                vo.setTeacherAvatar(u.getAvatar());
            }
        }
        return vo;
    }
}

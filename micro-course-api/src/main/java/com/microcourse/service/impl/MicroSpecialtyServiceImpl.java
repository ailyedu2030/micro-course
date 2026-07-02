package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCreateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyDetailVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtySquareVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyUpdateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.proposal.ChapterTeacherAssignment;
import com.microcourse.entity.proposal.ProposalChapter;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyFeaturedAudit;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyFeaturedAuditRepository;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.ProposalChapterRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import com.microcourse.service.MicroSpecialtyFeaturedService;
import com.microcourse.service.MicroSpecialtyQualityScoreService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.service.NotificationService;
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
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    private final ProposalChapterRepository proposalChapterRepository;
    private final ChapterTeacherAssignmentRepository chapterAssignRepo;

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
                                     @Lazy MicroSpecialtyFeaturedService featuredService,
                                     DepartmentRepository departmentRepository,
                                     CourseRepository courseRepository,
                                     ProposalChapterRepository proposalChapterRepository,
                                     ChapterTeacherAssignmentRepository chapterAssignRepo) {
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
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
        this.proposalChapterRepository = proposalChapterRepository;
        this.chapterAssignRepo = chapterAssignRepo;
    }

    // ====== 查询 ======

    @Override
    public PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params) {
        LambdaQueryWrapper<MicroSpecialty> wrapper = new LambdaQueryWrapper<>();

        String keyword = params != null ? (String) params.get("keyword") : null;
        String status = params != null ? (String) params.get("status") : null;

        // 学生只能看 RECRUITING 状态的微专业
        if (!SecurityUtil.isAdminOrAcademic()) {
            wrapper.eq(MicroSpecialty::getStatus, "RECRUITING");
        } else if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroSpecialty::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MicroSpecialty::getTitle, keyword);
        }
        // Featured / gold featured filters
        if (params != null && params.containsKey("featuredStatus")) {
            wrapper.eq(MicroSpecialty::getFeaturedStatus, params.get("featuredStatus").toString());
        }
        if (params != null && params.containsKey("isGoldFeatured")) {
            boolean isGold = Boolean.parseBoolean(params.get("isGoldFeatured").toString());
            wrapper.eq(MicroSpecialty::getIsGoldFeatured, isGold);
        }
        if (params != null && params.containsKey("featured") && Boolean.parseBoolean(params.get("featured").toString())) {
            wrapper.ne(MicroSpecialty::getFeaturedStatus, "NONE");
        }
        // Teacher role filter
        Long teacherId = SecurityUtil.getCurrentUserId();
        if (teacherId != null && params != null && params.containsKey("role")) {
            String roleFilter = (String) params.get("role");
            if ("leading".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role = 'LEAD')", teacherId);
            } else if ("participating".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role IN ('LEAD','MEMBER','ASSISTANT'))", teacherId);
            }
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
            if (lead != null) {
                vo.setLeadTeacherName(lead.getRealName());
            } else {
                vo.setLeadTeacherName("—");
            }
        }
        // Query department name
        if (ms.getOfferDepartmentId() != null) {
            Department dept = departmentRepository.selectById(ms.getOfferDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
        // Count courses
        Long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, ms.getId()));
        vo.setCourseCount(courseCount.intValue());
        return vo;
    }

    /**
     * G3: 7 天保护期判断。
     * 读取 admin_settings 中微专业 NEW 角标保护期配置 key="micro_specialty.new_protection_days"，
     * 若未配置或解析失败则使用默认值 7 天。
     */
    private boolean isNewlyCreated(MicroSpecialty ms) {
        if (ms.getApprovedAt() == null) return false;
        int protectionDays = 7; // 默认 7 天
        try {
            String setting = adminSettingService.getByKey("micro_specialty.new_protection_days");
            if (setting != null && !setting.isEmpty()) {
                protectionDays = Integer.parseInt(setting);
            }
        } catch (Exception e) {
            log.warn("读取新微专业保护期配置失败，使用默认值 7 天: {}", e.getMessage());
        }
        try {
            return ms.getApprovedAt().plusDays(protectionDays).isAfter(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MicroSpecialtyDetailVO getDetail(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 非管理员/教务/创建者/负责人不可查看 DRAFT / CANCELLED
        String status = ms.getStatus();
        Long userId = SecurityUtil.getCurrentUserId();
        boolean isAdminOrAcademic = SecurityUtil.isAdminOrAcademic();
        boolean isCreator = ms.getCreatorId().equals(userId);
        if (("DRAFT".equals(status) || "CANCELLED".equals(status))
                && !isAdminOrAcademic && !isCreator && !isLeadOf(id, userId)) {
            throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        }

        MicroSpecialtyDetailVO detail = new MicroSpecialtyDetailVO();
        copyToVO(ms, detail);

        // 预加载部门名、教师名
        if (ms.getOfferDepartmentId() != null) {
            Department dept = departmentRepository.selectById(ms.getOfferDepartmentId());
            if (dept != null) {
                detail.setDepartmentName(dept.getName());
            }
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

        // 计算平均成绩（来自已完成且已评分的报名记录）
        List<MicroSpecialtyEnrollment> completedEnrollments = msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                        .isNotNull(MicroSpecialtyEnrollment::getFinalScore));
        if (!completedEnrollments.isEmpty()) {
            double avg = completedEnrollments.stream()
                    .mapToDouble(e -> e.getFinalScore() != null ? e.getFinalScore().doubleValue() : 0.0)
                    .average().orElse(0.0);
            vo.setAverageScore(BigDecimal.valueOf(avg));
        }

        // 计算质量分
        vo.setQualityScore(qualityScoreService.calculate(ms.getId()));

        return vo;
    }

    // ====== CUD ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyVO create(MicroSpecialtyCreateRequest request) {
        // P2-C: 校验 code 唯一性（DB 唯一索引 uk_ms_code 在微专业表上存在但会被吞为通用错误）
        if (msRepository.selectCount(new LambdaQueryWrapper<MicroSpecialty>()
                .eq(MicroSpecialty::getCode, request.getCode())
                .isNull(MicroSpecialty::getDeletedAt)) > 0) {
            throw new BusinessException(ErrorCode.MICRO_SPECIALTY_CODE_EXISTS);
        }
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

        Long dbVersion = (long) ms.getVersion();

        // 状态编辑范围校验（§9.11）
        String status = ms.getStatus();
        if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "ARCHIVED".equals(status)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许编辑");
        }

        requireLeadOf(id);

        LambdaUpdateWrapper<MicroSpecialty> uw = new LambdaUpdateWrapper<MicroSpecialty>()
                .eq(MicroSpecialty::getId, id)
                .eq(MicroSpecialty::getVersion, dbVersion)
                .setSql("version = version + 1");
        if (request.getTitle() != null) uw.set(MicroSpecialty::getTitle, request.getTitle());
        if (request.getSubtitle() != null) uw.set(MicroSpecialty::getSubtitle, request.getSubtitle());
        if (request.getCoverUrl() != null) uw.set(MicroSpecialty::getCoverUrl, request.getCoverUrl());
        if (request.getDescription() != null) uw.set(MicroSpecialty::getDescription, request.getDescription());
        if (request.getTargetAudience() != null) uw.set(MicroSpecialty::getTargetAudience, request.getTargetAudience());
        if (request.getTrainingObjective() != null) uw.set(MicroSpecialty::getTrainingObjective, request.getTrainingObjective());
        if (request.getAdmissionRequirement() != null) uw.set(MicroSpecialty::getAdmissionRequirement, request.getAdmissionRequirement());
        if (request.getCompletionRule() != null) uw.set(MicroSpecialty::getCompletionRule, request.getCompletionRule());
        if (request.getSemester() != null) uw.set(MicroSpecialty::getSemester, request.getSemester());
        if (request.getMaxStudents() != null) uw.set(MicroSpecialty::getMaxStudents, request.getMaxStudents());
        uw.set(MicroSpecialty::getUpdatedAt, LocalDateTime.now());

        int affected = msRepository.update(null, uw);
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        return toVO(msRepository.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireOwnerOrLead(id);

        // 仅 DRAFT/REJECTED/ARCHIVED 可删除
        String s = ms.getStatus();
        if (!"DRAFT".equals(s) && !"REJECTED".equals(s) && !"ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许删除");
        }
        // FK 业务检查：有选课记录时禁止删除
        long enrollCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, id));
        if (enrollCount > 0) {
            throw new BusinessException(ErrorCode.MS_FORBIDDEN,
                    "该微专业已有选课记录，无法删除。请先取消微专业");
        }
        LambdaUpdateWrapper<MicroSpecialty> uw = new LambdaUpdateWrapper<MicroSpecialty>()
                .eq(MicroSpecialty::getId, id)
                .eq(MicroSpecialty::getVersion, ms.getVersion())
                .set(MicroSpecialty::getDeletedAt, LocalDateTime.now())
                .setSql("version = version + 1");
        int affected = msRepository.update(null, uw);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    // ====== 状态流转 ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(id);

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

        requireLeadOf(id);

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

        // P1-9: 通知所有已报名学生
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

        requireLeadOf(id);

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
                if (courseEn != null && !"CANCELLED".equals(courseEn.getEnrollmentStatus())) {
                    // Mark as CANCELLED instead of hard delete
                    enrollmentRepository.update(null,
                            new LambdaUpdateWrapper<Enrollment>()
                                    .eq(Enrollment::getId, courseEn.getId())
                                    .set(Enrollment::getEnrollmentStatus, "CANCELLED")
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
        // 信息泄露防护：公开端点不过滤 DRAFT/CANCELLED 状态（P2-1）
        // 与 getDetail() L287-295 采用一致的过滤规则：非管理员/教务/创建者/负责人不可查看 DRAFT/CANCELLED 的课程编排
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        String status = ms.getStatus();
        Long userId = SecurityUtil.getCurrentUserId();
        // 未登录用户（SecurityConfig permitAll）userId 为 null，isAdminOrAcademic/isLeadOf 均返回 false
        if (("DRAFT".equals(status) || "CANCELLED".equals(status))
                && userId != null
                && !SecurityUtil.isAdminOrAcademic()
                && !userId.equals(ms.getCreatorId())
                && !isLeadOf(msId, userId)) {
            throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        }
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

        requireLeadOf(msId);

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

        requireLeadOf(msId);

        // §9.11 编辑范围校验
        String s = ms.getStatus();

        // RECRUITING: only allow sort_order modification (version 乐观锁保护)
        if ("RECRUITING".equals(s)) {
            MicroSpecialtyCourse recItem = msCourseRepository.selectById(itemId);
            if (recItem == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
            int recVersion = recItem.getVersion();
            LambdaUpdateWrapper<MicroSpecialtyCourse> uw = new LambdaUpdateWrapper<MicroSpecialtyCourse>()
                    .eq(MicroSpecialtyCourse::getId, itemId)
                    .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId)
                    .eq(MicroSpecialtyCourse::getVersion, recVersion);
            if (request.getSortOrder() != null) {
                uw.set(MicroSpecialtyCourse::getSortOrder, request.getSortOrder());
            }
            uw.setSql("version = version + 1");
            int affected = msCourseRepository.update(null, uw);
            if (affected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }
            return toCourseVO(msCourseRepository.selectById(itemId));
        }

        if ("COMPLETED".equals(s) || "CANCELLED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许修改课程");
        }

        MicroSpecialtyCourse item = msCourseRepository.selectById(itemId);
        if (item == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        int oldVersion = item.getVersion();
        if (request.getSortOrder() != null) item.setSortOrder(request.getSortOrder());
        if (request.getIsRequired() != null) item.setIsRequired(request.getIsRequired());
        if (request.getCredits() != null) item.setCredits(request.getCredits());
        if (request.getHours() != null) item.setHours(request.getHours());
        if (request.getMinScore() != null) item.setMinScore(request.getMinScore());
        if (request.getRecommendedSemester() != null) item.setRecommendedSemester(request.getRecommendedSemester());

        LambdaUpdateWrapper<MicroSpecialtyCourse> uw = new LambdaUpdateWrapper<MicroSpecialtyCourse>()
                .eq(MicroSpecialtyCourse::getId, itemId)
                .eq(MicroSpecialtyCourse::getVersion, oldVersion)
                .setSql("version = version + 1");
        if (request.getSortOrder() != null) uw.set(MicroSpecialtyCourse::getSortOrder, request.getSortOrder());
        if (request.getIsRequired() != null) uw.set(MicroSpecialtyCourse::getIsRequired, request.getIsRequired());
        if (request.getCredits() != null) uw.set(MicroSpecialtyCourse::getCredits, request.getCredits());
        if (request.getHours() != null) uw.set(MicroSpecialtyCourse::getHours, request.getHours());
        if (request.getMinScore() != null) uw.set(MicroSpecialtyCourse::getMinScore, request.getMinScore());
        if (request.getRecommendedSemester() != null) uw.set(MicroSpecialtyCourse::getRecommendedSemester, request.getRecommendedSemester());
        int affected = msCourseRepository.update(null, uw);
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        return toCourseVO(msCourseRepository.selectById(itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourse(Long msId, Long itemId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(msId);

        // §9.11 编辑范围校验
        String s = ms.getStatus();
        if ("RECRUITING".equals(s) || "COMPLETED".equals(s) || "CANCELLED".equals(s) || "ARCHIVED".equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许移除课程");
        }

        MicroSpecialtyCourse item = msCourseRepository.selectById(itemId);
        if (item == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        int affected = msCourseRepository.delete(new LambdaQueryWrapper<MicroSpecialtyCourse>()
                .eq(MicroSpecialtyCourse::getId, itemId)
                .eq(MicroSpecialtyCourse::getVersion, item.getVersion()));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
    }

    // ====== 教师团队 ======

    @Override
    public List<MicroSpecialtyTeacherVO> listTeachers(Long msId) {
        // 信息泄露防护：公开端点不再暴露 INVITED/PENDING_ACADEMIC 等中间态邀请（P2-2）
        // 与 getDetail() 保持一致：仅返回 invite_status='ACTIVE' 的教师（已接受且未移除）
        List<MicroSpecialtyTeacher> teachers = msTeacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        return teachers.stream().map(this::toTeacherVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        requireLeadOf(msId);
        checkNotTerminal(ms);

        // 校验角色枚举
        if (!"LEAD".equals(request.getRole()) && !"MEMBER".equals(request.getRole()) && !"ASSISTANT".equals(request.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "角色必须为 LEAD/MEMBER/ASSISTANT");
        }

        // LEAD 唯一性校验
        if ("LEAD".equals(request.getRole())) {
            Long activeLeadCount = msTeacherRepository.selectCount(
                    new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                            .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                            .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
            if (activeLeadCount > 0) throw new BusinessException(ErrorCode.MS_LEAD_EXISTS);
        }

        // 校验课程归属
        if (request.getCourseId() != null) {
            Long courseCount = msCourseRepository.selectCount(
                    new LambdaQueryWrapper<MicroSpecialtyCourse>()
                            .eq(MicroSpecialtyCourse::getId, request.getCourseId())
                            .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId));
            if (courseCount == 0) throw new BusinessException(ErrorCode.COURSE_NOT_IN_MS);
        }

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

        // Phase 3: 多章节分配
        if (request.getChapterIds() != null && !request.getChapterIds().isEmpty()) {
            for (Long chapterId : request.getChapterIds()) {
                ProposalChapter chapter = proposalChapterRepository.selectById(chapterId);
                ChapterTeacherAssignment assign = new ChapterTeacherAssignment();
                assign.setChapterId(chapterId);
                assign.setTeacherId(request.getTeacherId());
                assign.setProposalId(msId);
                assign.setCourseId(chapter != null ? chapter.getCourseId() : null);
                assign.setSource("TBD");
                assign.setAcceptStatus("PENDING");
                chapterAssignRepo.insert(assign);
            }
        }

        notificationService.notifyAsync(request.getTeacherId(), NotificationType.MS_INVITE_TEAM,
                "微专业团队邀请", "您被邀请加入微专业《" + ms.getTitle() + "》团队", msId);

        return toTeacherVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTeacher(Long msId, Long teacherId) {
        requireLeadOf(msId);

        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        checkNotTerminal(ms);

        MicroSpecialtyTeacher teacher = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, teacherId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        if (teacher == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        // LEAD 禁止被直接移除, 需走 transferLeadership 流程
        if ("LEAD".equals(teacher.getRole())) {
            throw new BusinessException(ErrorCode.MS_LEAD_CANNOT_REMOVE);
        }

        LambdaUpdateWrapper<MicroSpecialtyTeacher> wrapper = new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                .eq(MicroSpecialtyTeacher::getId, teacher.getId())
                .eq(MicroSpecialtyTeacher::getVersion, teacher.getVersion())
                .setSql("version = version + 1")
                .set(MicroSpecialtyTeacher::getInviteStatus, "REMOVED")
                .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now());
        int affected = msTeacherRepository.update(null, wrapper);
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        notificationService.notifyAsync(teacherId, NotificationType.MS_TEAM_REMOVED,
                "已被移出微专业团队", "您已被移出微专业团队", msId);
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

    @Override
    public String getMyRole(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) return null;
        MicroSpecialtyTeacher record = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        return record != null ? record.getRole() : null;
    }

    /** 校验当前用户是否为微专业负责人或系统管理员 */
    @Override
    public void requireLeadOf(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (!isLeadOf(msId, userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.MS_FORBIDDEN, "您不是该微专业的LEAD，无权操作");
        }
    }

    /** 校验当前用户是否为微专业负责人/创建者或系统管理员 */
    private void requireOwnerOrLead(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (!isOwnerOrLead(msId, userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.MS_FORBIDDEN, "无权操作此微专业");
        }
    }

    /** 校验微专业是否为终态（CANCELLED/ARCHIVED），终态不允许教师邀请/移除操作 */
    private void checkNotTerminal(MicroSpecialty ms) {
        if ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus())) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }
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
        vo.setUpdatedAt(ms.getUpdatedAt());
        vo.setFeaturedApplyAt(ms.getFeaturedApplyAt());
        vo.setFeaturedApplyReason(ms.getFeaturedApplyReason());

        // Set department name
        Department dept = departmentRepository.selectById(ms.getOfferDepartmentId());
        if (dept != null) {
            vo.setDepartmentName(dept.getName());
        }

        // Set lead teacher name
        if (ms.getLeadTeacherId() != null) {
            User leadUser = userRepository.selectById(ms.getLeadTeacherId());
            if (leadUser != null) {
                vo.setLeadTeacherName(leadUser.getRealName());
            }
        }

        // Set creator name
        if (ms.getCreatorId() != null) {
            User creatorUser = userRepository.selectById(ms.getCreatorId());
            if (creatorUser != null) {
                vo.setCreatorName(creatorUser.getRealName());
            }
        }

        // Count courses
        Long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, ms.getId()));
        vo.setCourseCount(courseCount.intValue());

        // Count pending enrollments (待审报名)
        Long pendingCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "PENDING"));
        vo.setPendingEnrollCount(pendingCount.intValue());

        // Set teacher role for current user in this micro-specialty
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId != null) {
            MicroSpecialtyTeacher teacher = msTeacherRepository.selectOne(
                    new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, ms.getId())
                            .eq(MicroSpecialtyTeacher::getTeacherId, currentUserId));
            if (teacher != null) {
                vo.setRole(teacher.getRole());
            }
        }

        // Count total enrollments
        Long totalEnrollments = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId()));
        vo.setTotalEnrollments(totalEnrollments.intValue());
    }

    private MicroSpecialtyCourseVO toCourseVO(MicroSpecialtyCourse item) {
        MicroSpecialtyCourseVO vo = new MicroSpecialtyCourseVO();
        vo.setId(item.getId());
        vo.setMicroSpecialtyId(item.getMicroSpecialtyId());
        vo.setCourseId(item.getCourseId());
        vo.setSortOrder(item.getSortOrder());
        vo.setIsRequired(item.getIsRequired());
        vo.setCredits(item.getCredits());
        vo.setHours(item.getHours());
        vo.setMinScore(item.getMinScore());
        vo.setRecommendedSemester(item.getRecommendedSemester());
        // Query course details
        if (item.getCourseId() != null) {
            Course course = courseRepository.selectById(item.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCourseType(course.getCourseType());
                // set teacher name from course
                if (course.getTeacherId() != null) {
                    User teacher = userRepository.selectById(course.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    }
                }
            }
        }
        return vo;
    }

    private MicroSpecialtyTeacherVO toTeacherVO(MicroSpecialtyTeacher t) {
        MicroSpecialtyTeacherVO vo = new MicroSpecialtyTeacherVO();
        vo.setId(t.getId());
        vo.setMicroSpecialtyId(t.getMicroSpecialtyId());
        vo.setTeacherId(t.getTeacherId());
        vo.setRoleLabel(t.getRole());
        vo.setRole(t.getRole());
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
        // Query course title for this teacher's assignment
        if (t.getCourseId() != null) {
            Course course = courseRepository.selectById(t.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }
        return vo;
    }
}

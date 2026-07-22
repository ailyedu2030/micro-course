package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.BatchOperationResult;
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
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.User;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.proposal.ChapterTeacherAssignment;
import com.microcourse.entity.proposal.ProposalChapter;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.ProposalChapterRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.MicroSpecialtyAdminService;
import com.microcourse.service.MicroSpecialtyQueryService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class MicroSpecialtyServiceImpl implements MicroSpecialtyService {

    // ====== 微专业状态常量 ======
    private static final String MS_STATUS_DRAFT = "DRAFT";
    private static final String MS_STATUS_REJECTED = "REJECTED";
    private static final String MS_STATUS_COMPLETED = "COMPLETED";
    private static final String MS_STATUS_CANCELLED = "CANCELLED";
    private static final String MS_STATUS_ARCHIVED = "ARCHIVED";
    private static final String MS_STATUS_RECRUITING = "RECRUITING";
    private static final String FEATURED_STATUS_NONE = "NONE";

    // ====== 教师角色常量 ======
    private static final String TEACHER_ROLE_LEAD = "LEAD";
    private static final String TEACHER_ROLE_MEMBER = "MEMBER";
    private static final String TEACHER_ROLE_ASSISTANT = "ASSISTANT";

    // ====== 邀请状态常量 ======
    private static final String INVITE_STATUS_INVITED = "INVITED";
    private static final String INVITE_STATUS_ACTIVE = "ACTIVE";
    private static final String INVITE_STATUS_DECLINED = "DECLINED";
    private static final String INVITE_STATUS_REMOVED = "REMOVED";
    /** OP-0171: 跨院邀请需教务处审批 */
    private static final String INVITE_STATUS_PENDING_ACADEMIC = "PENDING_ACADEMIC";

    private final CourseRepository courseRepository;
    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    private final NotificationService notificationService;
    private final ProposalChapterRepository proposalChapterRepository;
    private final ChapterTeacherAssignmentRepository chapterAssignRepo;
    private final MicroSpecialtyQueryService queryService;
    private final MicroSpecialtyAdminService adminService;
    private final UserRepository userRepository;

    public MicroSpecialtyServiceImpl(CourseRepository courseRepository,
                                     MicroSpecialtyRepository msRepository,
                                     MicroSpecialtyCourseRepository msCourseRepository,
                                     MicroSpecialtyTeacherRepository msTeacherRepository,
                                     MicroSpecialtyEnrollmentRepository msEnrollmentRepository,
                                     NotificationService notificationService,
                                     ProposalChapterRepository proposalChapterRepository,
                                     ChapterTeacherAssignmentRepository chapterAssignRepo,
                                     MicroSpecialtyQueryService queryService,
                                     MicroSpecialtyAdminService adminService,
                                     UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.msEnrollmentRepository = msEnrollmentRepository;
        this.notificationService = notificationService;
        this.proposalChapterRepository = proposalChapterRepository;
        this.chapterAssignRepo = chapterAssignRepo;
        this.queryService = queryService;
        this.adminService = adminService;
        this.userRepository = userRepository;
    }

    // ====== 查询（委托 MicroSpecialtyQueryService） ======

    @Override
    public PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params) {
        return queryService.page(page, size, params);
    }

    @Override
    public MicroSpecialtySquareVO getSquareData() {
        return queryService.getSquareData();
    }

    @Override
    public MicroSpecialtyDetailVO getDetail(Long id) {
        return queryService.getDetail(id);
    }

    @Override
    public MicroSpecialtyStatsVO stats(Long id) {
        return queryService.stats(id);
    }

    @Override
    public List<MicroSpecialtyCourseVO> listCourses(Long msId) {
        return queryService.listCourses(msId);
    }

    @Override
    public List<MicroSpecialtyTeacherVO> listTeachers(Long msId) {
        return queryService.listTeachers(msId);
    }

    @Override
    public boolean isLeadOf(Long msId, Long userId) {
        return queryService.isLeadOf(msId, userId);
    }

    @Override
    public boolean isMemberOf(Long msId, Long userId) {
        return queryService.isMemberOf(msId, userId);
    }

    @Override
    public boolean isOwnerOrLead(Long msId, Long userId) {
        return queryService.isOwnerOrLead(msId, userId);
    }

    @Override
    public String getMyRole(Long msId) {
        return queryService.getMyRole(msId);
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
        ms.setStatus(MS_STATUS_DRAFT);
        ms.setCreatorId(SecurityUtil.getCurrentUserId());
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        ms.setVersion(0);
        ms.setIsFeatured(false);
        ms.setIsGoldFeatured(false);
        ms.setFeaturedStatus(FEATURED_STATUS_NONE);
        ms.setStudentCount(0);

        msRepository.insert(ms);

        // 自动创建 LEAD INVITED 记录
        MicroSpecialtyTeacher leadRecord = new MicroSpecialtyTeacher();
        leadRecord.setMicroSpecialtyId(ms.getId());
        leadRecord.setTeacherId(request.getLeadTeacherId());
        leadRecord.setRole(TEACHER_ROLE_LEAD);
        leadRecord.setInviteStatus(INVITE_STATUS_INVITED);
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

        return queryService.toVO(ms);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyVO update(Long id, MicroSpecialtyUpdateRequest request) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        Long dbVersion = (long) ms.getVersion();

        // 状态编辑范围校验（§9.11）
        String status = ms.getStatus();
        if (MS_STATUS_COMPLETED.equals(status) || MS_STATUS_CANCELLED.equals(status) || MS_STATUS_ARCHIVED.equals(status)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前状态不允许编辑");
        }

        adminService.requireLeadOf(id);

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

        return queryService.toVO(msRepository.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        adminService.requireOwnerOrLead(id);

        /* ---- 【I-19修复】delete() ARCHIVED 可删但 CANCELLED 不可删 ---- */
        /* 【根因】同为终态的 CANCELLED 未加入可删除列表，导致已取消的微专业无法清理
         * 【修复】增加 CANCELLED 到可删除状态列表
         * 【防止再发】所有终态资源的可删除性应以同一逻辑标准判断 */
        String s = ms.getStatus();
        if (!MS_STATUS_DRAFT.equals(s) && !MS_STATUS_REJECTED.equals(s) && !MS_STATUS_ARCHIVED.equals(s) && !MS_STATUS_CANCELLED.equals(s)) {
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

    // ====== 状态流转（委托 MicroSpecialtyAdminService） ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        adminService.submit(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        adminService.approve(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        adminService.reject(id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void open(Long id) {
        adminService.open(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        adminService.close(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        adminService.cancel(id, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reopen(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        String status = ms.getStatus();
        if (!MS_STATUS_COMPLETED.equals(status) && !MS_STATUS_CANCELLED.equals(status)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已结业或已取消的微专业可重新开课");
        }
        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, id)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getStatus, "APPROVED")
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        adminService.archive(id);
    }

    // ====== 课程编排 ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyCourseVO addCourse(Long msId, MicroSpecialtyCourseRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        adminService.requireLeadOf(msId);

        // §9.11 编辑范围：RECRUITING 后不允许添加课程（仅可排序）
        String s = ms.getStatus();
        if (MS_STATUS_RECRUITING.equals(s) || MS_STATUS_COMPLETED.equals(s) || MS_STATUS_CANCELLED.equals(s) || MS_STATUS_ARCHIVED.equals(s)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前微专业状态不允许添加课程");
        }

        // P1I-047: 校验课程存在且未软删除
        Course course = courseRepository.selectById(request.getCourseId());
        if (course == null || course.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程不存在或已删除");
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
        return queryService.toCourseVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyCourseVO updateCourseItem(Long msId, Long itemId, MicroSpecialtyCourseRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        adminService.requireLeadOf(msId);

        // §9.11 编辑范围校验
        String s = ms.getStatus();

        // RECRUITING: only allow sort_order modification (version 乐观锁保护)
        if (MS_STATUS_RECRUITING.equals(s)) {
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
            return queryService.toCourseVO(msCourseRepository.selectById(itemId));
        }

        if (MS_STATUS_COMPLETED.equals(s) || MS_STATUS_CANCELLED.equals(s) || MS_STATUS_ARCHIVED.equals(s)) {
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
        return queryService.toCourseVO(msCourseRepository.selectById(itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourse(Long msId, Long itemId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        adminService.requireLeadOf(msId);

        // §9.11 编辑范围校验
        String s = ms.getStatus();
        if (MS_STATUS_RECRUITING.equals(s) || MS_STATUS_COMPLETED.equals(s) || MS_STATUS_CANCELLED.equals(s) || MS_STATUS_ARCHIVED.equals(s)) {
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
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        adminService.requireLeadOf(msId);
        adminService.checkNotTerminal(ms);

        // 校验角色枚举
        if (!TEACHER_ROLE_LEAD.equals(request.getRole()) && !TEACHER_ROLE_MEMBER.equals(request.getRole()) && !TEACHER_ROLE_ASSISTANT.equals(request.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "角色必须为 LEAD/MEMBER/ASSISTANT");
        }

        // LEAD 唯一性校验
        if (TEACHER_ROLE_LEAD.equals(request.getRole())) {
            Long activeLeadCount = msTeacherRepository.selectCount(
                    new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                            .eq(MicroSpecialtyTeacher::getRole, TEACHER_ROLE_LEAD)
                            .eq(MicroSpecialtyTeacher::getInviteStatus, INVITE_STATUS_ACTIVE));
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
                        .notIn(MicroSpecialtyTeacher::getInviteStatus, INVITE_STATUS_DECLINED, INVITE_STATUS_REMOVED));
        if (existCount > 0) throw new BusinessException(ErrorCode.MS_DUPLICATE_TEACHER);

        // OP-0171: 跨院检测 — 被邀请教师所属院系与微专业开设院系不同时，直接进入教务处审批流程
        User invitedTeacher = userRepository.selectById(request.getTeacherId());
        boolean isCrossDept = false;
        if (invitedTeacher != null && ms.getOfferDepartmentId() != null && invitedTeacher.getDepartmentId() != null) {
            isCrossDept = !ms.getOfferDepartmentId().equals(invitedTeacher.getDepartmentId());
        }
        /* ---- 【I-21修复】inviteTeacher 跨院检测未豁免 ADMIN ---- */
        /* 【根因】跨院检测对所有角色一视同仁，ADMIN 跨院也应跳过 PENDING_ACADEMIC 直接生效
         * 【修复】ADMIN 跨院时重置 isCrossDept = false
         * 【防止再发】所有角色特权豁免必须枚举完全 */
        if (isCrossDept && SecurityUtil.isAdmin()) {
            isCrossDept = false;
        }

        MicroSpecialtyTeacher record = new MicroSpecialtyTeacher();
        record.setMicroSpecialtyId(msId);
        record.setTeacherId(request.getTeacherId());
        record.setRole(request.getRole());
        record.setCourseId(request.getCourseId());
        record.setResponsibility(request.getResponsibility());
        record.setInviteStatus(isCrossDept ? INVITE_STATUS_PENDING_ACADEMIC : INVITE_STATUS_INVITED);
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

        return queryService.toTeacherVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTeacher(Long msId, Long teacherId) {
        adminService.requireLeadOf(msId);

        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        adminService.checkNotTerminal(ms);

        MicroSpecialtyTeacher teacher = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, teacherId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, INVITE_STATUS_ACTIVE));
        if (teacher == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        // LEAD 禁止被直接移除, 需走 transferLeadership 流程
        if (TEACHER_ROLE_LEAD.equals(teacher.getRole())) {
            throw new BusinessException(ErrorCode.MS_LEAD_CANNOT_REMOVE);
        }

        LambdaUpdateWrapper<MicroSpecialtyTeacher> wrapper = new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                .eq(MicroSpecialtyTeacher::getId, teacher.getId())
                .eq(MicroSpecialtyTeacher::getVersion, teacher.getVersion())
                .setSql("version = version + 1")
                .set(MicroSpecialtyTeacher::getInviteStatus, INVITE_STATUS_REMOVED)
                .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now());
        int affected = msTeacherRepository.update(null, wrapper);
        if (affected == 0) throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);

        notificationService.notifyAsync(teacherId, NotificationType.MS_TEAM_REMOVED,
                "已被移出微专业团队", "您已被移出微专业团队", msId);
    }

    // ====== LEAD 继任（委托 MicroSpecialtyAdminService） ======

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request) {
        adminService.transferLeadership(msId, request);
    }

    // ====== 角色鉴权（§9.12） ======

    @Override
    public void requireLeadOf(Long msId) {
        adminService.requireLeadOf(msId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchApprove(java.util.List<Long> ids) {
        return adminService.batchApprove(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchReject(java.util.List<Long> ids, String reason) {
        return adminService.batchReject(ids, reason);
    }

}

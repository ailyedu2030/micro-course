package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.storage.*;
import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.User;
import com.microcourse.entity.proposal.*;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.NotificationService;
import com.microcourse.service.StorageApplicationCudService;
import com.microcourse.service.StorageApplicationImageStorageService;
import com.microcourse.service.StorageApplicationQueryService;
import com.microcourse.service.StorageApplicationService;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.StorageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 15: 微专业申请表 Storage Application Service 实现
 *
 * <p>核心职责：草稿 CRUD、动态子表先删后插、文件上传、
 * 预览构建、提交校验、模块重置。</p>
 */
@Service
public class StorageApplicationServiceImpl implements StorageApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationServiceImpl.class);

    private static final String MODULE_COURSES = "courses";

    private static final long AUTO_SAVE_MIN_INTERVAL_MS = 1000;

    private final ConcurrentHashMap<Long, Long> lastAutoSaveTime = new ConcurrentHashMap<>();
    private static final String MODULE_LEAD_COURSES = "leadCourses";
    private static final String MODULE_TEAM_MEMBERS = "teamMembers";
    private static final String MODULE_SIGNATURES = "signatures";

    /** 固定签字级别（不可重置，对齐 spec §7.2#11：保留 3 固定行） */
    private static final java.util.Set<String> FIXED_SIGN_LEVELS = java.util.Set.of("LEAD", "DEPT", "SCHOOL");
    private static final String MODULE_SHARED_UNITS = "sharedUnits";

    private final MicroSpecialtyProposalRepository proposalRepository;
    private final ProposalCourseRepository courseRepository;
    private final ProposalLeadCourseRepository leadCourseRepository;
    private final ProposalTeamMemberRepository teamMemberRepository;
    private final ProposalSignatureRepository signatureRepository;
    private final ProposalSharedUnitRepository sharedUnitRepository;
    private final UserRepository userRepository;
    private final ChapterTeacherAssignmentRepository assignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final StorageApplicationQueryService queryService;
    private final StorageApplicationCudService cudService;
    private final StorageApplicationImageStorageService imageStorageService;
    private final NotificationService notificationService;
    private final RedisUtil redisUtil;
    private final com.microcourse.service.MicroSpecialtyProposalService msProposalService;

    public StorageApplicationServiceImpl(
            MicroSpecialtyProposalRepository proposalRepository,
            ProposalCourseRepository courseRepository,
            ProposalLeadCourseRepository leadCourseRepository,
            ProposalTeamMemberRepository teamMemberRepository,
            ProposalSignatureRepository signatureRepository,
            ProposalSharedUnitRepository sharedUnitRepository,
            UserRepository userRepository,
            ChapterTeacherAssignmentRepository assignmentRepository,
            DepartmentRepository departmentRepository,
            StorageApplicationQueryService queryService,
            StorageApplicationCudService cudService,
            StorageApplicationImageStorageService imageStorageService,
            NotificationService notificationService,
            RedisUtil redisUtil,
            com.microcourse.service.MicroSpecialtyProposalService msProposalService) {
        this.proposalRepository = proposalRepository;
        this.courseRepository = courseRepository;
        this.leadCourseRepository = leadCourseRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.signatureRepository = signatureRepository;
        this.sharedUnitRepository = sharedUnitRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.departmentRepository = departmentRepository;
        this.queryService = queryService;
        this.cudService = cudService;
        this.imageStorageService = imageStorageService;
        this.notificationService = notificationService;
        this.redisUtil = redisUtil;
        this.msProposalService = msProposalService;
    }

    // ================================================================
    // 1. initDraft
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long initDraft(Long userId) {
        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setProposerId(userId);
        proposal.setTitle("");
        proposal.setType("急需紧缺型");
        proposal.setStatus("DRAFT");
        proposal.setCreatedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        proposal.setOfferDepartmentId(resolveOfferDepartmentId(userId));
        proposalRepository.insert(proposal);
        Long newId = proposal.getId();

        // P1-C-1 修复: 创建3行固定签字 (LEAD/DEPT/SCHOOL)
        // DB表有 DEFAULT CURRENT_TIMESTAMP, 自动填充时间
        String[] fixedLevels = {"LEAD", "DEPT", "SCHOOL"};
        for (int i = 0; i < fixedLevels.length; i++) {
            ProposalSignature sig = new ProposalSignature();
            sig.setProposalId(newId);
            sig.setSignLevel(fixedLevels[i]);
            sig.setUnitSeq(i);
            signatureRepository.insert(sig);
        }

        log.info("initDraft: userId={}, proposalId={}, departmentId={}",
            userId, newId, proposal.getOfferDepartmentId());
        return newId;
    }

    private Long resolveOfferDepartmentId(Long userId) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在，无法初始化申报草稿");
        }
        if (user.getDepartmentId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "当前教师账号未绑定学院，无法初始化申报草稿");
        }
        Department department = departmentRepository.selectById(user.getDepartmentId());
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, "当前教师所属学院不存在，无法初始化申报草稿");
        }
        return department.getId();
    }

    // ================================================================
    // 2. getMyDrafts
    // ================================================================
    @Override
    public PageResult<StorageApplicationSummaryVO> getMyDrafts(Long userId, int page, int size, String status) {
        return queryService.getMyDrafts(userId, page, size, status);
    }

    // ================================================================
    // 3. getDetail
    // ================================================================
    @Override
    public StorageApplicationVO getDetail(Long proposalId, Long userId) {
        return queryService.getDetail(proposalId, userId);
    }

    // ================================================================
    // 4. save（全量保存）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public StorageApplicationVO save(Long proposalId, Long userId, StorageApplicationSaveRequest request) {
        // C-002: Redis 分布式锁防止 save 与 autoSave/submit/reset 的竞态条件
        String lockKey = "storage:lock:" + proposalId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "操作过于频繁，请稍后重试");
        }
        try {
            MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
            if (proposal == null) {
                throw new BusinessException(ErrorCode.SA_NOT_FOUND);
            }
            if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }

            String status = proposal.getStatus();
            if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
                throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "仅草稿或已驳回状态可保存");
            }

            // 更新主表字段
            cudService.applyRequestToProposal(proposal, request);
            proposal.setUpdatedAt(LocalDateTime.now());
            if (proposalRepository.updateById(proposal) == 0) {
                throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "数据冲突，请重新加载后再试");
            }

            // 处理子表（先删后插，包含共享单位签字同步）
            cudService.replaceSubTables(proposalId, request, true);

            return queryService.getDetail(proposalId, userId);
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 5. autoSave（轻量级自动保存）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSave(Long proposalId, Long userId, StorageApplicationAutoSaveRequest request) {
        // 限流检查：1 秒内最多一次 autoSave
        long now = System.currentTimeMillis();
        Long lastTs = lastAutoSaveTime.get(proposalId);
        if (lastTs != null && (now - lastTs) < AUTO_SAVE_MIN_INTERVAL_MS) {
            log.debug("autoSave rate limited: proposalId={}, interval={}ms", proposalId, now - lastTs);
            return;
        }
        lastAutoSaveTime.put(proposalId, now);

        // C-002/C-003: Redis 分布式锁防止 autoSave 与 save/submit/reset 的竞态条件
        String lockKey = "storage:lock:" + proposalId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            log.warn("autoSave lock failed: proposal {} is being modified by another operation", proposalId);
            return;
        }
        try {
            MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
            if (proposal == null) {
                log.warn("autoSave skipped: proposal {} not found", proposalId);
                return;
            }
            if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
                log.warn("autoSave skipped: userId {} no permission for proposal {}", userId, proposalId);
                return;
            }

            // P0-1 修复：autoSave 对非可编辑状态静默跳过（不抛异常，因为是后台操作）
            String status = proposal.getStatus();
            if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
                log.debug("autoSave skipped: proposal {} status is {}", proposalId, status);
                return;
            }

            // 仅更新非空字段到主表
            cudService.applyRequestToProposal(proposal, request);
            proposal.setUpdatedAt(LocalDateTime.now());
            // P2-02: 记录自动保存时间
            proposal.setLastAutoSavedAt(LocalDateTime.now());
            // RT-1: 使用 @Version 乐观锁防止 autoSave 与 submit 的竞态条件
            // update(entity, wrapper) 将 WHERE 条件加入 version 检查，冲突时返回 0 行
            int rows = proposalRepository.update(proposal, new LambdaQueryWrapper<MicroSpecialtyProposal>()
                    .eq(MicroSpecialtyProposal::getId, proposalId)
                    .eq(MicroSpecialtyProposal::getVersion, proposal.getVersion()));
            if (rows == 0) {
                log.warn("autoSave conflict: proposal {} was modified by another operation (submit likely in progress)", proposalId);
                // autoSave 是后台操作，冲突时静默跳过，不抛异常
                return;
            }

            // 子表在 autoSave 时也进行替换，但共享单位签字仅在 full save 时同步
            cudService.replaceSubTables(proposalId, request, false);
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 6. uploadImage
    // ================================================================
    @Override
    public UploadResultVO uploadImage(Long proposalId, Long userId, MultipartFile file, String type) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return imageStorageService.storeImage(proposalId, file, type);
    }

    // ================================================================
    // 7. buildPreview
    // ================================================================
    @Override
    public StorageApplicationPreviewVO buildPreview(Long proposalId, Long userId) {
        return queryService.buildPreview(proposalId, userId);
    }

    // ================================================================
    // 8. submit
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long proposalId, Long userId) {
        // C-002: Redis 分布式锁防止 submit 与 save/autoSave/reset 的竞态条件
        String lockKey = "storage:lock:" + proposalId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "操作过于频繁，请稍后重试");
        }
        try {
            MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
            if (proposal == null) {
                throw new BusinessException(ErrorCode.SA_NOT_FOUND);
            }
            if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }

            String status = proposal.getStatus();
            if (!"DRAFT".equals(status) && !"REJECTED".equals(status)) {
                throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "仅草稿或已驳回状态可提交审核");
            }

            // 执行提交前校验 — 使用完整校验，与导出校验(validateForExport)分离
            StorageApplicationSaveRequest validationReq = queryService.buildValidationRequest(proposalId);
            List<String> submitErrors = StorageValidator.validateForSubmit(validationReq);
            // 追加子表存在性校验
            long courseCount = courseRepository.selectCount(
                new LambdaQueryWrapper<ProposalCourse>().eq(ProposalCourse::getProposalId, proposalId));
            long memberCount = teamMemberRepository.selectCount(
                new LambdaQueryWrapper<ProposalTeamMember>().eq(ProposalTeamMember::getProposalId, proposalId));
            long sigCount = signatureRepository.selectCount(
                new LambdaQueryWrapper<ProposalSignature>().eq(ProposalSignature::getProposalId, proposalId));
            if (courseCount == 0) submitErrors.add("课程表至少需要 1 门课程");
            if (memberCount == 0) submitErrors.add("教学团队至少需要 1 名成员");
            if (sigCount == 0) submitErrors.add("至少需要 1 个签字记录");
            
            if (!submitErrors.isEmpty()) {
                throw new BusinessException(ErrorCode.SA_FORM_INCOMPLETE,
                        "请补全以下必填项： " + String.join("; ", submitErrors));
            }

            proposal.setStatus("PENDING_REVIEW");
            proposal.setValidationPassed(true);
            proposal.setUpdatedAt(LocalDateTime.now());
            if (proposalRepository.updateById(proposal) == 0) {
                throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "数据已被其他操作修改，请刷新后重试");
            }
            log.info("submit: proposalId={}, userId={}", proposalId, userId);
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 9. resetModule
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetModule(Long proposalId, Long userId, String module) {
        // C-004: Redis 分布式锁防止 resetModule 与 save/autoSave/submit 的竞态条件
        String lockKey = "storage:lock:" + proposalId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "操作过于频繁，请稍后重试");
        }
        try {
            MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
            if (proposal == null) {
                throw new BusinessException(ErrorCode.SA_NOT_FOUND);
            }
            if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }

            // P0-6 修复：仅草稿和已驳回状态允许重置
            if (!"DRAFT".equals(proposal.getStatus()) && !"REJECTED".equals(proposal.getStatus())) {
                throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "仅草稿和已驳回状态的申请表可重置");
            }

            switch (module) {
                case MODULE_COURSES:
                    // P1-C-1 修复: 重置课程前检查是否有已接受的教师分配
                    // CASCADE DELETE 会连带删除 chapter_teacher_assignments
                    if (assignmentRepository.selectCount(
                            new LambdaQueryWrapper<ChapterTeacherAssignment>()
                                    .eq(ChapterTeacherAssignment::getProposalId, proposalId)
                                    .eq(ChapterTeacherAssignment::getAcceptStatus, "ACCEPTED")) > 0) {
                        throw new BusinessException(ErrorCode.MS_STATUS_INVALID,
                                "已有已接受的教师分配，无法重置课程模块");
                    }
                    courseRepository.delete(new LambdaQueryWrapper<ProposalCourse>()
                            .eq(ProposalCourse::getProposalId, proposalId));
                    break;
                case MODULE_LEAD_COURSES:
                    leadCourseRepository.delete(new LambdaQueryWrapper<ProposalLeadCourse>()
                            .eq(ProposalLeadCourse::getProposalId, proposalId));
                    break;
                case MODULE_TEAM_MEMBERS:
                    teamMemberRepository.delete(new LambdaQueryWrapper<ProposalTeamMember>()
                            .eq(ProposalTeamMember::getProposalId, proposalId));
                    break;
                case MODULE_SIGNATURES:
                    // P1-2 修复：对齐 spec §7.2#11 — 仅清空非固定签字（SHARED_UNIT），
                    // 固定 LEAD/DEPT/SCHOOL 三行保留但清空签字内容（图片/文字/日期）
                    signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                            .eq(ProposalSignature::getProposalId, proposalId)
                            .eq(ProposalSignature::getSignLevel, "SHARED_UNIT"));
                    resetFixedSignatureContents(proposalId);
                    break;
                case MODULE_SHARED_UNITS:
                    sharedUnitRepository.delete(new LambdaQueryWrapper<ProposalSharedUnit>()
                            .eq(ProposalSharedUnit::getProposalId, proposalId));
                    // P0-2 修复：重置共享单位时也清除对应的签字记录
                    signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                            .eq(ProposalSignature::getProposalId, proposalId)
                            .eq(ProposalSignature::getSignLevel, "SHARED_UNIT"));
                    break;
                default:
                    throw new BusinessException(ErrorCode.SA_MODULE_NOT_FOUND, "未知模块: " + module);
            }

            proposal.setUpdatedAt(LocalDateTime.now());
            proposalRepository.updateById(proposal);
            log.info("resetModule: proposalId={}, module={}", proposalId, redact(module));
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    // ================================================================
    // 10. resetAll
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAll(Long proposalId, Long userId) {
        // C-004: Redis 分布式锁防止 resetAll 与 save/autoSave/submit 的竞态条件
        String lockKey = "storage:lock:" + proposalId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, 30);
        if (!locked) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "操作过于频繁，请稍后重试");
        }
        try {
            MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
            if (proposal == null) {
                throw new BusinessException(ErrorCode.SA_NOT_FOUND);
            }
            if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }

            // P0-6 修复：仅草稿和已驳回状态允许重置
            if (!"DRAFT".equals(proposal.getStatus()) && !"REJECTED".equals(proposal.getStatus())) {
                throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "仅草稿和已驳回状态的申请表可重置");
            }

            // P1-3 修复：对齐 spec §7.2#12 — resetAll 只清空子表，
            // 主表基础信息(title/proposer/department)保留，避免教师误操作丢失工作
            proposal.setUpdatedAt(LocalDateTime.now());
            proposalRepository.updateById(proposal);

            // 删除所有子表数据
            courseRepository.delete(new LambdaQueryWrapper<ProposalCourse>()
                    .eq(ProposalCourse::getProposalId, proposalId));
            leadCourseRepository.delete(new LambdaQueryWrapper<ProposalLeadCourse>()
                    .eq(ProposalLeadCourse::getProposalId, proposalId));
            teamMemberRepository.delete(new LambdaQueryWrapper<ProposalTeamMember>()
                    .eq(ProposalTeamMember::getProposalId, proposalId));
            signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                    .eq(ProposalSignature::getProposalId, proposalId));
            sharedUnitRepository.delete(new LambdaQueryWrapper<ProposalSharedUnit>()
                    .eq(ProposalSharedUnit::getProposalId, proposalId));

            // P1-3 修复：对齐 spec §7.2#12 — resetAll 后必须重新初始化 3 行固定签字
            initFixedSignatures(proposalId);

            log.info("resetAll: proposalId={}", proposalId);
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    /**
     * 重新初始化 3 行固定签字（LEAD/DEPT/SCHOOL）。
     * 在 resetAll 后调用，确保主签字位永不丢失。
     */
    private void initFixedSignatures(Long proposalId) {
        String[] fixedLevels = {"LEAD", "DEPT", "SCHOOL"};
        for (int i = 0; i < fixedLevels.length; i++) {
            ProposalSignature sig = new ProposalSignature();
            sig.setProposalId(proposalId);
            sig.setSignLevel(fixedLevels[i]);
            sig.setUnitSeq(i);
            signatureRepository.insert(sig);
        }
    }

    /**
     * 清空固定签字行（LEAD/DEPT/SCHOOL）的内容（图片/文字/日期），
     * 保留签字位本身。
     */
    private void resetFixedSignatureContents(Long proposalId) {
        for (String level : FIXED_SIGN_LEVELS) {
            ProposalSignature sig = signatureRepository.selectOne(
                    new LambdaQueryWrapper<ProposalSignature>()
                            .eq(ProposalSignature::getProposalId, proposalId)
                            .eq(ProposalSignature::getSignLevel, level));
            if (sig != null) {
                sig.setOpinionText(null);
                sig.setSignatureType(null);
                sig.setSignatureText(null);
                sig.setSignatureImageUrl(null);
                sig.setSealImageUrl(null);
                sig.setSignDate(null);
                sig.setRemark(null);
                signatureRepository.updateById(sig);
            }
        }
    }

    // ================================================================
    // P1C-091: 审批流程（ACADEMIC）
    // ================================================================

    @Override
    public PageResult<StorageApplicationSummaryVO> getPendingList(int page, int size) {
        return queryService.getPendingList(page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long proposalId, Long reviewerId) {
        // 委托 MicroSpecialtyProposalService 执行完整的审批+创建微专业流程
        msProposalService.approveAndCreateSpecialty(proposalId, reviewerId);
        log.info("storage application approved: proposalId={}, reviewerId={}", proposalId, reviewerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long proposalId, Long reviewerId, String reason) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!"PENDING_REVIEW".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.SA_STATUS_INVALID, "仅待审核状态的申请表可驳回");
        }
        String safeReason = reason != null && !reason.isBlank() ? reason : "未填写驳回原因";
        // 乐观锁更新
        int affected = proposalRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MicroSpecialtyProposal>()
                        .eq(MicroSpecialtyProposal::getId, proposalId)
                        .eq(MicroSpecialtyProposal::getStatus, "PENDING_REVIEW")
                        .eq(MicroSpecialtyProposal::getVersion, proposal.getVersion())
                        .set(MicroSpecialtyProposal::getStatus, "REJECTED")
                        .set(MicroSpecialtyProposal::getReviewedBy, reviewerId)
                        .set(MicroSpecialtyProposal::getReviewedAt, LocalDateTime.now())
                        .set(MicroSpecialtyProposal::getReviewComment, safeReason)
                        .set(MicroSpecialtyProposal::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "该申请表已被其他操作修改，请刷新后重试");
        }
        // 通知申报人
        try {
            notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_PROPOSAL_REJECTED,
                    "申批被驳回", "您的微专业申请表被驳回，原因：" + safeReason, proposalId);
        } catch (Exception e) {
            log.warn("通知驳回失败: proposalId={}", proposalId, e);
        }
        log.info("storage application rejected: proposalId={}, reviewerId={}", proposalId, reviewerId);
    }

    @Override
    public void validateOwner(Long proposalId, Long userId) {
        queryService.validateOwner(proposalId, userId);
    }

    // ================================================================
    // 11. validateForExport
    // ================================================================
    @Override
    public ExportValidationResult validateForExport(Long proposalId, Long userId) {
        return queryService.validateForExport(proposalId, userId);
    }

    // ================================================================
    // 12. resolveSchoolName
    // ================================================================
    @Override
    public String resolveSchoolName(Long proposalId) {
        try {
            MicroSpecialtyProposal p = proposalRepository.selectById(proposalId);
            // P2-03: 优先使用 universityFullName，fallback 到 title
            String name = "申报高校";
            if (p != null) {
                name = p.getUniversityFullName();
                if (name == null || name.isBlank()) {
                    name = p.getTitle();
                }
                if (name == null) name = "申报高校";
            }
            // Sanitize: remove characters unsafe for filenames across OS
            String sanitized = name.replaceAll("[/\\\\:*?\"<>|]", "").trim();
            // S-010: length limit and character whitelist
            sanitized = sanitized.replaceAll("[^\\u4e00-\\u9fa5\\w\\-（）()]", "").trim();
            if (sanitized.length() > 50) {
                sanitized = sanitized.substring(0, 50);
            }
            return sanitized.isEmpty() ? "申报高校" : sanitized;
        } catch (Exception e) {
            return "申报高校";
        }
    }

    // ================================================================
    // 内部辅助方法
    // ================================================================

    // buildVO, buildPreview(MicroSpecialtyProposal), buildRequest, build*Items,
    // lookupUserName, buildAssignmentItems — extracted to StorageApplicationQueryServiceImpl

    /**
     * S-008: 日志脱敏辅助方法 — 对用户输入截断至 50 字符，防止敏感信息泄露至日志。
     */
    private static String redact(String input) {
        if (input == null) return null;
        return input.length() > 50 ? input.substring(0, 50) + "..." : input;
    }

}

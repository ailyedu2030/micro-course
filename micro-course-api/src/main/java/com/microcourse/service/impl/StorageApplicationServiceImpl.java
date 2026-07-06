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
import com.microcourse.service.StorageApplicationQueryService;
import com.microcourse.service.StorageApplicationService;
import com.microcourse.util.FileUploadUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.StorageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final int TARGET_IMAGE_SIZE = 150;

    private static final long AUTO_SAVE_MIN_INTERVAL_MS = 1000;

    private final ConcurrentHashMap<Long, Long> lastAutoSaveTime = new ConcurrentHashMap<>();
    private static final String MODULE_LEAD_COURSES = "leadCourses";
    private static final String MODULE_TEAM_MEMBERS = "teamMembers";
    private static final String MODULE_SIGNATURES = "signatures";
    private static final String MODULE_SHARED_UNITS = "sharedUnits";

    private final MicroSpecialtyProposalRepository proposalRepository;
    private final ProposalCourseRepository courseRepository;
    private final ProposalChapterRepository chapterRepository;
    private final ProposalLeadCourseRepository leadCourseRepository;
    private final ProposalTeamMemberRepository teamMemberRepository;
    private final ProposalSignatureRepository signatureRepository;
    private final ProposalSharedUnitRepository sharedUnitRepository;
    private final UserRepository userRepository;
    private final ChapterTeacherAssignmentRepository assignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final StorageApplicationQueryService queryService;
    private final StorageApplicationCudService cudService;
    private final NotificationService notificationService;
    private final com.microcourse.service.MicroSpecialtyProposalService msProposalService;

    public StorageApplicationServiceImpl(
            MicroSpecialtyProposalRepository proposalRepository,
            ProposalCourseRepository courseRepository,
            ProposalChapterRepository chapterRepository,
            ProposalLeadCourseRepository leadCourseRepository,
            ProposalTeamMemberRepository teamMemberRepository,
            ProposalSignatureRepository signatureRepository,
            ProposalSharedUnitRepository sharedUnitRepository,
            UserRepository userRepository,
            ChapterTeacherAssignmentRepository assignmentRepository,
            DepartmentRepository departmentRepository,
            StorageApplicationQueryService queryService,
            StorageApplicationCudService cudService,
            NotificationService notificationService,
            com.microcourse.service.MicroSpecialtyProposalService msProposalService) {
        this.proposalRepository = proposalRepository;
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.leadCourseRepository = leadCourseRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.signatureRepository = signatureRepository;
        this.sharedUnitRepository = sharedUnitRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.departmentRepository = departmentRepository;
        this.queryService = queryService;
        this.cudService = cudService;
        this.notificationService = notificationService;
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
        // 自动填充所属学院
        try {
            com.microcourse.entity.User user = userRepository.selectById(userId);
            if (user != null && user.getDepartmentId() != null) {
                proposal.setOfferDepartmentId(user.getDepartmentId());
            } else {
                // 用户无学院时，兜底取第一个可用学院
                List<com.microcourse.entity.Department> depts = departmentRepository.selectList(null);
                if (depts != null && !depts.isEmpty()) {
                    proposal.setOfferDepartmentId(depts.get(0).getId());
                    log.warn("initDraft: userId={} 无学院，兜底使用 departmentId={}", userId, depts.get(0).getId());
                }
            }
        } catch (Exception e) {
            log.warn("initDraft: 无法获取用户学院信息, userId={}", userId);
        }
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

    // ================================================================
    // 2. getMyDrafts
    // ================================================================
    @Override
    public PageResult<StorageApplicationSummaryVO> getMyDrafts(Long userId, int page, int size) {
        return queryService.getMyDrafts(userId, page, size);
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
    }

    // ================================================================
    // 5. autoSave（轻量级自动保存）
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void autoSave(Long proposalId, Long userId, StorageApplicationSaveRequest request) {
        // 限流检查：1 秒内最多一次 autoSave
        long now = System.currentTimeMillis();
        Long lastTs = lastAutoSaveTime.get(proposalId);
        if (lastTs != null && (now - lastTs) < AUTO_SAVE_MIN_INTERVAL_MS) {
            log.debug("autoSave rate limited: proposalId={}, interval={}ms", proposalId, now - lastTs);
            return;
        }
        lastAutoSaveTime.put(proposalId, now);

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
    }

    // ================================================================
    // 6. uploadImage
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultVO uploadImage(Long proposalId, Long userId, MultipartFile file, String type) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // P2-4 fix (S-004): validate filename to prevent path traversal
        FileUploadUtil.assertSafeFilename(file.getOriginalFilename());

        // 校验文件
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE, "文件名不能为空");
        }

        String lowerName = originalFilename.toLowerCase();
        if (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE);
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_TOO_LARGE);
        }

        // P2-2 fix (S-004): verify file content via magic bytes (not just extension)
        // I-02 fix: cross-validate extension vs content magic
        boolean isJpegMagic = false;
        boolean isPngMagic = false;
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header, 0, 8);
            if (read >= 8) {
                // JPEG: FF D8 FF
                if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                    isJpegMagic = true;
                }
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                if (header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E &&
                    header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A &&
                    header[6] == 0x1A && header[7] == 0x0A) {
                    isPngMagic = true;
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "无法读取文件内容");
        }
        if (!isJpegMagic && !isPngMagic) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "文件内容不是有效的 jpg/png 图片");
        }
        // I-02 fix: cross-validate extension vs content magic to prevent PNG disguised as .jpg
        boolean isJpegExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg");
        boolean isPngExt = lowerName.endsWith(".png");
        if (isJpegMagic && !isJpegExt) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "文件内容与扩展名不匹配：JPEG 内容需使用 .jpg/.jpeg 扩展名");
        }
        if (isPngMagic && !isPngExt) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "文件内容与扩展名不匹配：PNG 内容需使用 .png 扩展名");
        }

        // 保存文件到本地 uploads 目录
        try {
            String uploadDir = "uploads/storage/" + proposalId;
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // B6 fix: delete old signature/seal files of the same type to prevent accumulation
            if (Files.exists(uploadPath)) {
                try (var dirStream = Files.newDirectoryStream(uploadPath, type + "_*")) {
                    for (Path oldFile : dirStream) {
                        Files.deleteIfExists(oldFile);
                        log.info("Deleted old image: {}", oldFile);
                    }
                } catch (IOException e) {
                    log.warn("Failed to clean old images for proposalId={}, type={}", proposalId, type, e);
                }
            }

            String ext = lowerName.endsWith(".png") ? ".png" : ".jpg";
            String newFileName = type + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path destPath = uploadPath.resolve(newFileName);

            // 缩放图片至 150×150
            try (InputStream is = file.getInputStream()) {
                BufferedImage original = ImageIO.read(is);
                if (original != null) {
                    BufferedImage resized = new BufferedImage(TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = resized.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(original, 0, 0, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, null);
                    g.dispose();
                    ImageIO.write(resized, ext.equals(".png") ? "png" : "jpg", destPath.toFile());
                } else {
                    file.transferTo(destPath.toFile());
                }
            } catch (Exception e) {
                log.warn("Image resize failed, saving original: proposalId={}", proposalId, e);
                file.transferTo(destPath.toFile());
            }

            String url = "/" + uploadDir + "/" + newFileName;

            return new UploadResultVO(url, originalFilename, file.getSize());
        } catch (IOException e) {
            log.error("uploadImage failed: proposalId={}", proposalId, e);
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "图片上传失败");
        }
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
        proposal.setUpdatedAt(LocalDateTime.now());
        if (proposalRepository.updateById(proposal) == 0) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "数据已被其他操作修改，请刷新后重试");
        }
        log.info("submit: proposalId={}, userId={}", proposalId, userId);
    }

    // ================================================================
    // 9. resetModule
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetModule(Long proposalId, Long userId, String module) {
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
                signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                        .eq(ProposalSignature::getProposalId, proposalId));
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
        log.info("resetModule: proposalId={}, module={}", proposalId, module);
    }

    // ================================================================
    // 10. resetAll
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetAll(Long proposalId, Long userId) {
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

        // 重置主表字段
        proposal.setTitle("");
        proposal.setMicroSpecialtyName(null);
        proposal.setTargetAudience(null);
        proposal.setTargetDisciplines(null);
        proposal.setTotalCredits(null);
        proposal.setCourseCount(null);
        proposal.setCoBuildUniversities(null);
        proposal.setPlannedShareUniversities(null);
        proposal.setEnrollmentQuota(null);
        proposal.setClassSize(null);
        proposal.setStartDate(null);
        proposal.setDuration(null);
        proposal.setIsIndustryAcademic(null);
        proposal.setIndustryPartners(null);
        proposal.setIntroduction(null);
        proposal.setMarketDemandAnalysis(null);
        proposal.setSpecialtyOverview(null);
        proposal.setCurriculumDesign(null);
        proposal.setConstructionGuarantee(null);
        proposal.setLeadName(null);
        proposal.setLeadTitle(null);
        proposal.setLeadPosition(null);
        proposal.setLeadPhone(null);
        proposal.setLeadResearchDirection(null);
        proposal.setLeadMainTasks(null);
        proposal.setContactPhone(null);
        proposal.setApplyDate(null);
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

        log.info("resetAll: proposalId={}", proposalId);
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


}

package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.storage.*;
import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.User;
import com.microcourse.entity.proposal.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.StorageApplicationService;
import com.microcourse.util.FileUploadUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.StorageValidator;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final SqlSessionFactory sqlSessionFactory;

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
            SqlSessionFactory sqlSessionFactory) {
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
        this.sqlSessionFactory = sqlSessionFactory;
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
        log.info("initDraft: userId={}, proposalId={}, departmentId={}",
            userId, proposal.getId(), proposal.getOfferDepartmentId());
        return proposal.getId();
    }

    // ================================================================
    // 2. getMyDrafts
    // ================================================================
    @Override
    public PageResult<StorageApplicationSummaryVO> getMyDrafts(Long userId, int page, int size) {
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<MicroSpecialtyProposal>()
                        .eq(MicroSpecialtyProposal::getProposerId, userId)
                        .orderByDesc(MicroSpecialtyProposal::getUpdatedAt));

        List<MicroSpecialtyProposal> proposals = ipage.getRecords();

        Set<Long> deptIds = proposals.stream()
                .map(MicroSpecialtyProposal::getOfferDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            List<Department> depts = departmentRepository.selectBatchIds(deptIds);
            deptNameMap = depts.stream()
                    .collect(Collectors.toMap(Department::getId, Department::getName));
        }

        List<StorageApplicationSummaryVO> result = new ArrayList<>();
        for (MicroSpecialtyProposal p : proposals) {
            StorageApplicationSummaryVO vo = new StorageApplicationSummaryVO();
            vo.setId(p.getId());
            vo.setTitle(p.getTitle());
            vo.setMicroSpecialtyName(p.getMicroSpecialtyName());
            vo.setStatus(p.getStatus());
            vo.setType(p.getType());
            vo.setDepartmentName(deptNameMap.getOrDefault(p.getOfferDepartmentId(), ""));
            vo.setCreatedAt(p.getCreatedAt());
            vo.setUpdatedAt(p.getUpdatedAt());
            result.add(vo);
        }

        PageResult<StorageApplicationSummaryVO> pr = new PageResult<>();
        pr.setItems(result);
        pr.setPage(page);
        pr.setSize(size);
        pr.setTotalElements(ipage.getTotal());
        pr.setTotalPages(ipage.getPages());
        return pr;
    }

    // ================================================================
    // 3. getDetail
    // ================================================================
    @Override
    public StorageApplicationVO getDetail(Long proposalId, Long userId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }

        // 权限校验：本人或 ADMIN
        if (userId != null && !SecurityUtil.isOwnerOrAdmin(proposal.getProposerId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return buildVO(proposal);
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
        applyRequestToProposal(proposal, request);
        proposal.setUpdatedAt(LocalDateTime.now());
        if (proposalRepository.updateById(proposal) == 0) {
            throw new BusinessException(ErrorCode.SA_AUTO_SAVE_CONFLICT, "数据冲突，请重新加载后再试");
        }

        // 处理子表（先删后插，包含共享单位签字同步）
        replaceSubTables(proposalId, request, true);

        return buildVO(proposal);
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
        applyRequestToProposal(proposal, request);
        proposal.setUpdatedAt(LocalDateTime.now());
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
        replaceSubTables(proposalId, request, false);
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
        boolean validMagic = false;
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int read = is.read(header, 0, 8);
            if (read >= 8) {
                // JPEG: FF D8 FF
                if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                    validMagic = true;
                }
                // PNG: 89 50 4E 47 0D 0A 1A 0A
                if (header[0] == (byte)0x89 && header[1] == 0x50 && header[2] == 0x4E &&
                    header[3] == 0x47 && header[4] == 0x0D && header[5] == 0x0A &&
                    header[6] == 0x1A && header[7] == 0x0A) {
                    validMagic = true;
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "无法读取文件内容");
        }
        if (!validMagic) {
            throw new BusinessException(ErrorCode.SA_SIGNATURE_IMAGE_INVALID_TYPE,
                "文件内容不是有效的 jpg/png 图片");
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
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return buildPreview(proposal);
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

        // 执行提交前校验（使用 validateAndThrow 确保异常路径不会被遗漏）
        StorageApplicationSaveRequest req = buildRequest(proposal);
        StorageValidator.validateAndThrow(req);

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

    @Override
    public void validateOwner(Long proposalId, Long userId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    // ================================================================
    // 11. validateForExport
    // ================================================================
    @Override
    public ExportValidationResult validateForExport(Long proposalId, Long userId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        StorageApplicationSaveRequest req = buildRequest(proposal);
        List<String> errors = StorageValidator.validateForSubmit(req);

        ExportValidationResult result = new ExportValidationResult();
        for (String error : errors) {
            result.addError(error);
        }

        // 额外检查：导出模板存在性由导出 Service 检查，此处仅校验数据
        return result;
    }

    // ================================================================
    // 内部辅助方法
    // ================================================================

    /**
     * 将主表 Entity 转换为 VO
     */
    private StorageApplicationVO buildVO(MicroSpecialtyProposal proposal) {
        StorageApplicationVO vo = new StorageApplicationVO();
        vo.setId(proposal.getId());
        vo.setStatus(proposal.getStatus());

        // 模块1：表头
        vo.setTitle(proposal.getTitle());
        vo.setMicroSpecialtyName(proposal.getMicroSpecialtyName());
        vo.setLeadName(proposal.getLeadName());
        vo.setContactPhone(proposal.getContactPhone());
        vo.setApplyDate(proposal.getApplyDate() != null ?
            java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d").format(proposal.getApplyDate()) : null);

        // 模块2：基本情况
        vo.setType(proposal.getType());
        vo.setTargetAudience(proposal.getTargetAudience());
        vo.setTargetDisciplines(proposal.getTargetDisciplines());
        vo.setTotalCredits(proposal.getTotalCredits());
        vo.setCourseCount(proposal.getCourseCount());
        vo.setCoBuildUniversities(proposal.getCoBuildUniversities());
        vo.setPlannedShareUniversities(proposal.getPlannedShareUniversities());
        vo.setEnrollmentQuota(proposal.getEnrollmentQuota());
        vo.setClassSize(proposal.getClassSize());
        vo.setStartDate(proposal.getStartDate() != null ? proposal.getStartDate().toString() : null);
        vo.setDuration(proposal.getDuration());
        vo.setIsIndustryAcademic(proposal.getIsIndustryAcademic());
        vo.setIndustryPartners(proposal.getIndustryPartners());

        // 富文本
        vo.setIntroduction(proposal.getIntroduction());
        vo.setMarketDemandAnalysis(proposal.getMarketDemandAnalysis());
        vo.setSpecialtyOverview(proposal.getSpecialtyOverview());
        vo.setCurriculumDesign(proposal.getCurriculumDesign());
        vo.setConstructionGuarantee(proposal.getConstructionGuarantee());

        // 模块3：教学团队
        vo.setLeadTitle(proposal.getLeadTitle());
        vo.setLeadPosition(proposal.getLeadPosition());
        vo.setLeadPhone(proposal.getLeadPhone());
        vo.setLeadResearchDirection(proposal.getLeadResearchDirection());
        vo.setLeadMainTasks(proposal.getLeadMainTasks());

        // 子表数据
        vo.setCourses(buildCourseItems(proposal.getId()));
        vo.setLeadCourses(buildLeadCourseItems(proposal.getId()));
        vo.setTeamMembers(buildTeamMemberItems(proposal.getId()));
        vo.setSignatures(buildSignatureItems(proposal.getId()));
        vo.setSharedUnits(buildSharedUnitItems(proposal.getId()));
        vo.setChapterAssignments(buildAssignmentItems(proposal.getId()));

        // 关联查询字段
        vo.setProposerName(lookupUserName(proposal.getProposerId()));
        if (proposal.getOfferDepartmentId() != null) {
            Department dept = departmentRepository.selectById(proposal.getOfferDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
        vo.setCreatedAt(proposal.getCreatedAt());
        vo.setUpdatedAt(proposal.getUpdatedAt());

        return vo;
    }

    /**
     * 构建预览 VO
     */
    private StorageApplicationPreviewVO buildPreview(MicroSpecialtyProposal proposal) {
        StorageApplicationPreviewVO vo = new StorageApplicationPreviewVO();
        vo.setId(proposal.getId());
        vo.setTitle(proposal.getTitle());
        vo.setMicroSpecialtyName(proposal.getMicroSpecialtyName());
        vo.setStatus(proposal.getStatus());
        vo.setLeadName(proposal.getLeadName());
        vo.setContactPhone(proposal.getContactPhone());
        vo.setApplyDate(proposal.getApplyDate() != null ?
            java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d").format(proposal.getApplyDate()) : null);
        vo.setType(proposal.getType());
        vo.setTargetAudience(proposal.getTargetAudience());
        vo.setTargetDisciplines(proposal.getTargetDisciplines());
        vo.setTotalCredits(proposal.getTotalCredits());
        vo.setCourseCount(proposal.getCourseCount());
        vo.setCoBuildUniversities(proposal.getCoBuildUniversities());
        vo.setPlannedShareUniversities(proposal.getPlannedShareUniversities());
        vo.setEnrollmentQuota(proposal.getEnrollmentQuota());
        vo.setClassSize(proposal.getClassSize());
        vo.setStartDate(proposal.getStartDate() != null ? proposal.getStartDate().toString() : null);
        vo.setDuration(proposal.getDuration());
        vo.setIsIndustryAcademic(proposal.getIsIndustryAcademic());
        vo.setIndustryPartners(proposal.getIndustryPartners());
        vo.setIntroduction(proposal.getIntroduction());
        vo.setMarketDemandAnalysis(proposal.getMarketDemandAnalysis());
        vo.setSpecialtyOverview(proposal.getSpecialtyOverview());
        vo.setCurriculumDesign(proposal.getCurriculumDesign());
        vo.setConstructionGuarantee(proposal.getConstructionGuarantee());
        vo.setLeadTitle(proposal.getLeadTitle());
        vo.setLeadPosition(proposal.getLeadPosition());
        vo.setLeadResearchDirection(proposal.getLeadResearchDirection());
        vo.setLeadMainTasks(proposal.getLeadMainTasks());

        vo.setCourses(buildCourseItems(proposal.getId()));
        vo.setLeadCourses(buildLeadCourseItems(proposal.getId()));
        vo.setTeamMembers(buildTeamMemberItems(proposal.getId()));
        vo.setSignatures(buildSignatureItems(proposal.getId()));
        vo.setSharedUnits(buildSharedUnitItems(proposal.getId()));
        vo.setChapterAssignments(buildAssignmentItems(proposal.getId()));

        return vo;
    }

    /**
     * 从 proposal 构建 StorageApplicationSaveRequest（用于校验）
     */
    private StorageApplicationSaveRequest buildRequest(MicroSpecialtyProposal proposal) {
        StorageApplicationSaveRequest req = new StorageApplicationSaveRequest();
        req.setTitle(proposal.getTitle());
        req.setLeadName(proposal.getLeadName());
        req.setContactPhone(proposal.getContactPhone());
        req.setApplyDate(proposal.getApplyDate() != null ?
                java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d").format(proposal.getApplyDate()) : null);
        req.setType(proposal.getType());
        req.setTargetAudience(proposal.getTargetAudience());
        req.setTargetDisciplines(proposal.getTargetDisciplines());
        req.setTotalCredits(proposal.getTotalCredits());
        req.setCourseCount(proposal.getCourseCount());
        req.setCoBuildUniversities(proposal.getCoBuildUniversities());
        req.setPlannedShareUniversities(proposal.getPlannedShareUniversities());
        req.setEnrollmentQuota(proposal.getEnrollmentQuota());
        req.setClassSize(proposal.getClassSize());
        req.setStartDate(proposal.getStartDate() != null ? proposal.getStartDate().toString() : null);
        req.setDuration(proposal.getDuration());
        req.setIsIndustryAcademic(proposal.getIsIndustryAcademic());
        req.setIndustryPartners(proposal.getIndustryPartners());
        req.setIntroduction(proposal.getIntroduction());
        req.setMarketDemandAnalysis(proposal.getMarketDemandAnalysis());
        req.setSpecialtyOverview(proposal.getSpecialtyOverview());
        req.setCurriculumDesign(proposal.getCurriculumDesign());
        req.setConstructionGuarantee(proposal.getConstructionGuarantee());
        req.setLeadTitle(proposal.getLeadTitle());
        req.setLeadPosition(proposal.getLeadPosition());
        req.setLeadPhone(proposal.getLeadPhone());
        req.setLeadResearchDirection(proposal.getLeadResearchDirection());
        req.setLeadMainTasks(proposal.getLeadMainTasks());
        req.setOfferDepartmentId(proposal.getOfferDepartmentId());
        req.setCourses(buildCourseItems(proposal.getId()));
        req.setLeadCourses(buildLeadCourseItems(proposal.getId()));
        req.setTeamMembers(buildTeamMemberItems(proposal.getId()));
        req.setSignatures(buildSignatureItems(proposal.getId()));
        req.setSharedUnits(buildSharedUnitItems(proposal.getId()));
        req.setChapterAssignments(buildAssignmentItems(proposal.getId()));
        return req;
    }

    /**
     * 对标 ProposalCourseItem 字段，将请求对象映射到主表 Entity
     */
    private void applyRequestToProposal(MicroSpecialtyProposal proposal, StorageApplicationSaveRequest request) {
        if (request.getTitle() != null) {
            proposal.setTitle(request.getTitle());
        }
        // P0-4 修复：持久化微专业名称
        if (request.getMicroSpecialtyName() != null) {
            proposal.setMicroSpecialtyName(request.getMicroSpecialtyName());
        }
        if (request.getLeadName() != null) {
            proposal.setLeadName(request.getLeadName());
        }
        if (request.getContactPhone() != null) {
            proposal.setContactPhone(request.getContactPhone());
        }
        // P1-C-1 修复：解析请求中的日期字符串，而非设为 now()
        if (request.getApplyDate() != null && !request.getApplyDate().isEmpty()) {
            try {
                proposal.setApplyDate(parseDate(request.getApplyDate()));
            } catch (Exception e) {
                log.warn("applyDate parse failed: {}", request.getApplyDate(), e);
            }
        }
        if (request.getType() != null) {
            proposal.setType(request.getType());
        }
        if (request.getTargetAudience() != null) {
            proposal.setTargetAudience(request.getTargetAudience());
        }
        if (request.getTargetDisciplines() != null) {
            proposal.setTargetDisciplines(request.getTargetDisciplines());
        }
        if (request.getTotalCredits() != null) {
            proposal.setTotalCredits(request.getTotalCredits());
        }
        if (request.getCourseCount() != null) {
            proposal.setCourseCount(request.getCourseCount());
        }
        if (request.getCoBuildUniversities() != null) {
            proposal.setCoBuildUniversities(request.getCoBuildUniversities());
        }
        if (request.getPlannedShareUniversities() != null) {
            proposal.setPlannedShareUniversities(request.getPlannedShareUniversities());
        }
        if (request.getEnrollmentQuota() != null) {
            proposal.setEnrollmentQuota(request.getEnrollmentQuota());
        }
        if (request.getClassSize() != null) {
            proposal.setClassSize(request.getClassSize());
        }
        // P1-C-1 修复：解析请求中的日期字符串，而非设为 now()
        if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
            try {
                proposal.setStartDate(parseDate(request.getStartDate()));
            } catch (Exception e) {
                log.warn("startDate parse failed: {}", request.getStartDate(), e);
            }
        }
        if (request.getDuration() != null) {
            proposal.setDuration(request.getDuration());
        }
        if (request.getIsIndustryAcademic() != null) {
            proposal.setIsIndustryAcademic(request.getIsIndustryAcademic());
        }
        if (request.getIndustryPartners() != null) {
            proposal.setIndustryPartners(request.getIndustryPartners());
        }
        if (request.getIntroduction() != null) {
            proposal.setIntroduction(request.getIntroduction());
        }
        if (request.getMarketDemandAnalysis() != null) {
            proposal.setMarketDemandAnalysis(request.getMarketDemandAnalysis());
        }
        if (request.getSpecialtyOverview() != null) {
            proposal.setSpecialtyOverview(request.getSpecialtyOverview());
        }
        if (request.getCurriculumDesign() != null) {
            proposal.setCurriculumDesign(request.getCurriculumDesign());
        }
        if (request.getConstructionGuarantee() != null) {
            proposal.setConstructionGuarantee(request.getConstructionGuarantee());
        }
        if (request.getLeadTitle() != null) {
            proposal.setLeadTitle(request.getLeadTitle());
        }
        if (request.getLeadPosition() != null) {
            proposal.setLeadPosition(request.getLeadPosition());
        }
        if (request.getLeadPhone() != null) {
            proposal.setLeadPhone(request.getLeadPhone());
        }
        if (request.getLeadResearchDirection() != null) {
            proposal.setLeadResearchDirection(request.getLeadResearchDirection());
        }
        if (request.getLeadMainTasks() != null) {
            proposal.setLeadMainTasks(request.getLeadMainTasks());
        }
        // A1 修复：持久化申报院系 ID
        if (request.getOfferDepartmentId() != null) {
            proposal.setOfferDepartmentId(request.getOfferDepartmentId());
        }
    }

    /**
     * 先删后插替换子表数据。
     * A2/A4/A6 修复：仅当子表数组不为 null 时才执行删除+插入，防止 autoSave 传入空数组导致数据丢失。
     * 事务保护由调用方 @Transactional 保证，本方法要求 Propagation.MANDATORY。
     *
     * @param proposalId         申请表 ID
     * @param request            保存请求（子表数组为 null 表示不更新该子表）
     * @param includeSharedUnits true=全量保存（含共享单位签字同步）；false=自动保存（跳过共享单位）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceSubTables(Long proposalId, StorageApplicationSaveRequest request, boolean includeSharedUnits) {
        // A6 修复：运行为确保处于活跃事务中
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("replaceSubTables 必须在事务上下文中调用");
        }

        // courses — A2: 仅当 courses 数组非 null 时更新 (R-002: 批量插入)
        if (request.getCourses() != null) {
            courseRepository.delete(new LambdaQueryWrapper<ProposalCourse>()
                    .eq(ProposalCourse::getProposalId, proposalId));
            int sortOrder = 0;
            List<ProposalCourse> entities = new ArrayList<>();
            for (ProposalCourseItem item : request.getCourses()) {
                ProposalCourse entity = new ProposalCourse();
                entity.setProposalId(proposalId);
                entity.setModuleName(item.getModuleName());
                entity.setCourseName(item.getCourseName());
                entity.setHours(item.getHours());
                entity.setCredits(item.getCredits());
                entity.setSemester(item.getSemester());
                entity.setSortOrder(sortOrder++);
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalCourseRepository batchRepo = sqlSession.getMapper(ProposalCourseRepository.class);
                    for (ProposalCourse entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.flushStatements();  // 必须先 flush 让 course.id 可用
                }

                // Phase 1: 同步保存章节(嵌套在课程循环内,确保 course.id 可用)
                // P0-1 修复: 维护 oldId → newChapter 映射,DELETE+INSERT 后前端旧ID不再有效
                // P1-C-1 修复: 同时维护 oldToNewCourseIdMap 用于 courseId 回退查询
                List<ProposalChapter> allChapters = new ArrayList<>();
                Map<Long, ProposalChapter> oldIdToNewChapterMap = new HashMap<>();
                Map<Long, Long> oldToNewCourseIdMap = new HashMap<>();
                for (int i = 0; i < request.getCourses().size(); i++) {
                    ProposalCourseItem item = request.getCourses().get(i);
                    ProposalCourse courseEntity = entities.get(i);
                    // 记录旧→新课程序号映射
                    if (item.getId() != null) {
                        oldToNewCourseIdMap.put(item.getId(), courseEntity.getId());
                    }
                    if (item.getChapters() != null && !item.getChapters().isEmpty()) {
                        int chapterSort = 0;
                        for (ProposalChapterItem chItem : item.getChapters()) {
                            ProposalChapter ch = new ProposalChapter();
                            ch.setProposalId(proposalId);
                            ch.setCourseId(courseEntity.getId());
                            ch.setTitle(chItem.getTitle());
                            ch.setDescription(chItem.getDescription());
                            ch.setHours(chItem.getHours());
                            ch.setSortOrder(chapterSort++);
                            allChapters.add(ch);
                            if (chItem.getId() != null) {
                                oldIdToNewChapterMap.put(chItem.getId(), ch);
                            }
                        }
                    }
                }
                if (!allChapters.isEmpty()) {
                    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                        ProposalChapterRepository chRepo = sqlSession.getMapper(ProposalChapterRepository.class);
                        for (ProposalChapter ch : allChapters) {
                            chRepo.insert(ch);
                        }
                        sqlSession.commit();
                    }
                }

                // Phase 2: 章节-教师分配同步
                // P0-1 修复: 用 oldIdToNewChapterMap 获取新章节 ID,解决 DELETE+INSERT 后
                // 前端旧 ID 导致 FK 约束 violation
                // P1-C-1 修复: 当映射找不到时,用 resolvedCourseId 在 allChapters 中回退匹配,
                // 仍然找不到则跳过该条 assignment 并记 warning,避免用过期 ID 插入 FK 崩溃
                if (request.getChapterAssignments() != null && !request.getChapterAssignments().isEmpty()) {
                    assignmentRepository.delete(new LambdaQueryWrapper<ChapterTeacherAssignment>()
                            .eq(ChapterTeacherAssignment::getProposalId, proposalId));
                    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                        ChapterTeacherAssignmentRepository batchRepo = sqlSession.getMapper(ChapterTeacherAssignmentRepository.class);
                        for (ChapterAssignmentItem assignItem : request.getChapterAssignments()) {
                            Long oldChapterId = assignItem.getChapterId();
                            ProposalChapter mappedCh = oldIdToNewChapterMap.get(oldChapterId);

                            // 回退查找: 当直接 ID 映射失败时(如章节刚创建无旧 ID),
                            // 解析新课程序号后在 allChapters 中按课程匹配第一个章节
                            if (mappedCh == null) {
                                Long resolvedCourseId = oldToNewCourseIdMap.getOrDefault(
                                        assignItem.getCourseId(), assignItem.getCourseId());
                                for (ProposalChapter fallbackCh : allChapters) {
                                    if (Objects.equals(fallbackCh.getCourseId(), resolvedCourseId)) {
                                        mappedCh = fallbackCh;
                                        break;
                                    }
                                }
                            }

                            if (mappedCh == null) {
                                // P0-1 修复: 找不到匹配章节时跳过,避免用过期 ID 触发 FK 约束崩溃
                                log.warn("replaceSubTables: 跳过 assignment(courseId={}, chapterId={}, teacherId={}) — 未找到匹配章节",
                                        assignItem.getCourseId(), oldChapterId, assignItem.getTeacherId());
                                continue;
                            }

                            Long newChapterId = mappedCh.getId();
                            Long newCourseId = mappedCh.getCourseId();
                            ChapterTeacherAssignment entity = new ChapterTeacherAssignment();
                            entity.setProposalId(proposalId);
                            entity.setCourseId(newCourseId);
                            entity.setChapterId(newChapterId);
                            entity.setTeacherId(assignItem.getTeacherId());
                            entity.setSource("TBD");
                            entity.setAcceptStatus("PENDING");
                            batchRepo.insert(entity);
                        }
                        sqlSession.commit();
                    }
                }
            }
        }

        // leadCourses — A2: 仅当 leadCourses 数组非 null 时更新 (R-002: 批量插入)
        if (request.getLeadCourses() != null) {
            leadCourseRepository.delete(new LambdaQueryWrapper<ProposalLeadCourse>()
                    .eq(ProposalLeadCourse::getProposalId, proposalId));
            int sortOrder = 0;
            List<ProposalLeadCourse> entities = new ArrayList<>();
            for (ProposalLeadCourseItem item : request.getLeadCourses()) {
                ProposalLeadCourse entity = new ProposalLeadCourse();
                entity.setProposalId(proposalId);
                entity.setCourseName(item.getCourseName());
                entity.setCredits(item.getCredits());
                entity.setHours(item.getHours());
                entity.setSortOrder(sortOrder++);
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalLeadCourseRepository batchRepo = sqlSession.getMapper(ProposalLeadCourseRepository.class);
                    for (ProposalLeadCourse entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // teamMembers — A2: 仅当 teamMembers 数组非 null 时更新 (R-002: 批量插入)
        if (request.getTeamMembers() != null) {
            teamMemberRepository.delete(new LambdaQueryWrapper<ProposalTeamMember>()
                    .eq(ProposalTeamMember::getProposalId, proposalId));
            List<ProposalTeamMember> entities = new ArrayList<>();
            for (ProposalTeamMemberItem item : request.getTeamMembers()) {
                ProposalTeamMember entity = new ProposalTeamMember();
                entity.setProposalId(proposalId);
                entity.setMemberType(item.getMemberType() != null ? item.getMemberType() : "MEMBER");
                entity.setSeq(item.getSeq());
                entity.setName(item.getName());
                entity.setAge(item.getAge());
                entity.setTitle(item.getTitle());
                entity.setOrganization(item.getOrganization());
                entity.setProfession(item.getProfession());
                entity.setTaughtCourses(item.getTaughtCourses());
                entity.setPlannedCourses(item.getPlannedCourses());
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalTeamMemberRepository batchRepo = sqlSession.getMapper(ProposalTeamMemberRepository.class);
                    for (ProposalTeamMember entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // signatures — A2: 仅当 signatures 数组非 null 时更新（不处理 SHARED_UNIT 级别）(R-002: 批量插入)
        if (request.getSignatures() != null) {
            signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                    .eq(ProposalSignature::getProposalId, proposalId)
                    .ne(ProposalSignature::getSignLevel, "SHARED_UNIT"));
            int sigSeq = 0;
            List<ProposalSignature> entities = new ArrayList<>();
            for (ProposalSignatureItem item : request.getSignatures()) {
                ProposalSignature entity = new ProposalSignature();
                entity.setProposalId(proposalId);
                entity.setSignLevel(item.getSignLevel());
                entity.setUnitSeq(sigSeq++);
                entity.setOpinionText(item.getOpinionText());
                entity.setSignatureType(item.getSignatureType());
                entity.setSignatureText(item.getSignatureText());
                entity.setSignatureImageUrl(item.getSignatureImageUrl());
                entity.setSealImageUrl(item.getSealImageUrl());
                entity.setSignDate(parseDate(item.getSignDate()));
                entity.setRemark(item.getRemark());
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSignatureRepository batchRepo = sqlSession.getMapper(ProposalSignatureRepository.class);
                    for (ProposalSignature entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // sharedUnits — 仅 full save 时处理，且仅当 sharedUnits 数组非 null 时更新 (R-002: 批量插入)
        if (includeSharedUnits && request.getSharedUnits() != null) {
            sharedUnitRepository.delete(new LambdaQueryWrapper<ProposalSharedUnit>()
                    .eq(ProposalSharedUnit::getProposalId, proposalId));
            // 先清除旧的 SHARED_UNIT 级别签字，再重新插入
            signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                    .eq(ProposalSignature::getProposalId, proposalId)
                    .eq(ProposalSignature::getSignLevel, "SHARED_UNIT"));
            int sortOrder = 0;
            List<ProposalSharedUnit> unitEntities = new ArrayList<>();
            List<ProposalSignature> sigEntities = new ArrayList<>();
            for (ProposalSharedUnitItem item : request.getSharedUnits()) {
                ProposalSharedUnit entity = new ProposalSharedUnit();
                entity.setProposalId(proposalId);
                entity.setUnitName(item.getUnitName());
                entity.setUnitType(item.getUnitType());
                entity.setSortOrder(sortOrder);
                unitEntities.add(entity);

                // 同步共享单位签字数据到 proposal_signatures 表
                ProposalSignature sig = new ProposalSignature();
                sig.setProposalId(proposalId);
                sig.setSignLevel("SHARED_UNIT");
                sig.setUnitSeq(sortOrder);
                sig.setOpinionText(item.getOpinionText());
                if (item.getSignature() != null) {
                    sig.setSignatureType(item.getSignature().getType());
                    sig.setSignatureText(item.getSignature().getText());
                    sig.setSignatureImageUrl(item.getSignature().getImageUrl());
                }
                if (item.getSeal() != null) {
                    sig.setSealImageUrl(item.getSeal().getImageUrl());
                }
                sig.setSignDate(item.getSignDate() != null && !item.getSignDate().isEmpty()
                        ? parseDate(item.getSignDate()) : null);
                sig.setRemark(item.getRemark());
                sigEntities.add(sig);

                sortOrder++;
            }
            // Batch insert shared units
            if (!unitEntities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSharedUnitRepository batchRepo = sqlSession.getMapper(ProposalSharedUnitRepository.class);
                    for (ProposalSharedUnit entity : unitEntities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
            // Batch insert shared unit signatures
            if (!sigEntities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSignatureRepository batchRepo = sqlSession.getMapper(ProposalSignatureRepository.class);
                    for (ProposalSignature entity : sigEntities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }
    }

    // ================================================================
    // 子表 Items 构建
    // ================================================================

    private List<ProposalCourseItem> buildCourseItems(Long proposalId) {
        List<ProposalCourse> entities = courseRepository.selectList(
                new LambdaQueryWrapper<ProposalCourse>()
                        .eq(ProposalCourse::getProposalId, proposalId)
                        .orderByAsc(ProposalCourse::getSortOrder));
        List<ProposalCourseItem> items = new ArrayList<>();
        for (ProposalCourse e : entities) {
            ProposalCourseItem item = new ProposalCourseItem();
            item.setId(e.getId());
            item.setModuleName(e.getModuleName());
            item.setCourseName(e.getCourseName());
            item.setHours(e.getHours());
            item.setCredits(e.getCredits());
            item.setSemester(e.getSemester());
            // Phase 1: 加载该课程的章节
            item.setChapters(buildChapterItems(e.getId()));
            items.add(item);
        }
        return items;
    }

    private List<ProposalChapterItem> buildChapterItems(Long courseId) {
        List<ProposalChapter> entities = chapterRepository.selectList(
                new LambdaQueryWrapper<ProposalChapter>()
                        .eq(ProposalChapter::getCourseId, courseId)
                        .orderByAsc(ProposalChapter::getSortOrder));
        List<ProposalChapterItem> items = new ArrayList<>();
        for (ProposalChapter e : entities) {
            ProposalChapterItem item = new ProposalChapterItem();
            item.setId(e.getId());
            item.setTitle(e.getTitle());
            item.setDescription(e.getDescription());
            item.setHours(e.getHours());
            item.setSortOrder(e.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private List<ProposalLeadCourseItem> buildLeadCourseItems(Long proposalId) {
        List<ProposalLeadCourse> entities = leadCourseRepository.selectList(
                new LambdaQueryWrapper<ProposalLeadCourse>()
                        .eq(ProposalLeadCourse::getProposalId, proposalId)
                        .orderByAsc(ProposalLeadCourse::getSortOrder));
        List<ProposalLeadCourseItem> items = new ArrayList<>();
        for (ProposalLeadCourse e : entities) {
            ProposalLeadCourseItem item = new ProposalLeadCourseItem();
            item.setId(e.getId());
            item.setCourseName(e.getCourseName());
            item.setCredits(e.getCredits());
            item.setHours(e.getHours());
            items.add(item);
        }
        return items;
    }

    private List<ProposalTeamMemberItem> buildTeamMemberItems(Long proposalId) {
        List<ProposalTeamMember> entities = teamMemberRepository.selectList(
                new LambdaQueryWrapper<ProposalTeamMember>()
                        .eq(ProposalTeamMember::getProposalId, proposalId)
                        .orderByAsc(ProposalTeamMember::getSeq));
        List<ProposalTeamMemberItem> items = new ArrayList<>();
        for (ProposalTeamMember e : entities) {
            ProposalTeamMemberItem item = new ProposalTeamMemberItem();
            item.setId(e.getId());
            item.setMemberType(e.getMemberType());
            item.setSeq(e.getSeq());
            item.setName(e.getName());
            item.setAge(e.getAge());
            item.setTitle(e.getTitle());
            item.setOrganization(e.getOrganization());
            item.setProfession(e.getProfession());
            item.setTaughtCourses(e.getTaughtCourses());
            item.setPlannedCourses(e.getPlannedCourses());
            items.add(item);
        }
        return items;
    }

    private List<ProposalSignatureItem> buildSignatureItems(Long proposalId) {
        List<ProposalSignature> entities = signatureRepository.selectList(
                new LambdaQueryWrapper<ProposalSignature>()
                        .eq(ProposalSignature::getProposalId, proposalId)
                        .orderByAsc(ProposalSignature::getUnitSeq));
        List<ProposalSignatureItem> items = new ArrayList<>();
        for (ProposalSignature e : entities) {
            ProposalSignatureItem item = new ProposalSignatureItem();
            item.setId(e.getId());
            item.setSignLevel(e.getSignLevel());
            item.setOpinionText(e.getOpinionText());
            item.setSignatureType(e.getSignatureType());
            item.setSignatureText(e.getSignatureText());
            item.setSignatureImageUrl(e.getSignatureImageUrl());
            item.setSealImageUrl(e.getSealImageUrl());
            item.setSignDate(e.getSignDate() != null ? e.getSignDate().toString() : null);
            item.setRemark(e.getRemark());
            items.add(item);
        }
        return items;
    }

    private List<ProposalSharedUnitItem> buildSharedUnitItems(Long proposalId) {
        List<ProposalSharedUnit> entities = sharedUnitRepository.selectList(
                new LambdaQueryWrapper<ProposalSharedUnit>()
                        .eq(ProposalSharedUnit::getProposalId, proposalId)
                        .orderByAsc(ProposalSharedUnit::getSortOrder));

        // P0-2 修复：加载 SHARED_UNIT 级别签字，按 unitSeq 索引
        List<ProposalSignature> sigs = signatureRepository.selectList(
                new LambdaQueryWrapper<ProposalSignature>()
                        .eq(ProposalSignature::getProposalId, proposalId)
                        .eq(ProposalSignature::getSignLevel, "SHARED_UNIT"));
        java.util.Map<Integer, ProposalSignature> sigMap = new java.util.HashMap<>();
        for (ProposalSignature sig : sigs) {
            if (sig.getUnitSeq() != null) {
                sigMap.put(sig.getUnitSeq(), sig);
            }
        }

        List<ProposalSharedUnitItem> items = new ArrayList<>();
        for (ProposalSharedUnit e : entities) {
            ProposalSharedUnitItem item = new ProposalSharedUnitItem();
            item.setId(e.getId());
            item.setUnitName(e.getUnitName());
            item.setUnitType(e.getUnitType());
            item.setSortOrder(e.getSortOrder());

            // P0-2 修复：从 proposal_signatures 回填签字数据
            ProposalSignature sig = sigMap.get(e.getSortOrder());
            if (sig != null) {
                item.setOpinionText(sig.getOpinionText());
                item.setSignature(new ProposalSignatureItem.SignatureFile(
                        sig.getSignatureType(), sig.getSignatureText(), sig.getSignatureImageUrl()));
                item.setSeal(new ProposalSignatureItem.SignatureFile(
                        null, null, sig.getSealImageUrl()));
                item.setSignDate(sig.getSignDate() != null ? sig.getSignDate().toString() : null);
                item.setRemark(sig.getRemark());
            }

            items.add(item);
        }
        return items;
    }

    private String lookupUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.selectById(userId);
        return user != null ? user.getRealName() : null;
    }

    // Phase 2: 加载章节-教师分配
    private List<ChapterAssignmentItem> buildAssignmentItems(Long proposalId) {
        List<ChapterTeacherAssignment> entities = assignmentRepository.selectList(
                new LambdaQueryWrapper<ChapterTeacherAssignment>()
                        .eq(ChapterTeacherAssignment::getProposalId, proposalId)
                        .eq(ChapterTeacherAssignment::getDeletedAt, null));
        List<ChapterAssignmentItem> items = new ArrayList<>();
        for (ChapterTeacherAssignment e : entities) {
            ChapterAssignmentItem item = new ChapterAssignmentItem();
            item.setId(e.getId());
            item.setCourseId(e.getCourseId());
            item.setChapterId(e.getChapterId());
            item.setTeacherId(e.getTeacherId());
            item.setSource(e.getSource() != null ? e.getSource() : "TBD");
            item.setAcceptStatus(e.getAcceptStatus() != null ? e.getAcceptStatus() : "PENDING");
            ProposalChapter ch = chapterRepository.selectById(e.getChapterId());
            if (ch != null) {
                item.setChapterTitle(ch.getTitle());
                ProposalCourse course = courseRepository.selectById(ch.getCourseId());
                if (course != null) {
                    item.setCourseTitle(course.getCourseName());
                }
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 解析前端传来的日期字符串，支持多种格式。
     * A3 修复：解析失败时打印 ERROR 级别日志（含实际输入），不再静默忽略。
     */
    private static LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        try {
            // yyyy.M 格式（如 "2025.9"）
            if (trimmed.matches("\\d{4}\\.\\d{1,2}")) {
                java.time.YearMonth ym = java.time.YearMonth.parse(trimmed,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy.M"));
                return ym.atDay(1).atStartOfDay();
            }
            // yyyy.M.d 格式（如 "2025.9.15"）
            if (trimmed.matches("\\d{4}\\.\\d{1,2}\\.\\d{1,2}")) {
                return LocalDateTime.parse(trimmed + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d HH:mm:ss"));
            }
            // yyyy-MM 格式（如 "2025-09"）
            if (trimmed.matches("\\d{4}-\\d{1,2}") && !trimmed.contains("T") && !trimmed.contains(" ")) {
                java.time.YearMonth ym = java.time.YearMonth.parse(trimmed,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                return ym.atDay(1).atStartOfDay();
            }
            // ISO 格式 / yyyy-MM-dd / yyyy-MM-dd'T'HH:mm:ss / yyyy-MM-dd HH:mm:ss
            return LocalDateTime.parse(trimmed.replace(" ", "T"),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            String redacted = dateStr.length() > 20 ? dateStr.substring(0, 10) + "..." : dateStr;
            log.error("日期解析失败: input='{}' (len={}), error={}", redacted, dateStr.length(), e.getMessage());
            return null;
        }
    }
}

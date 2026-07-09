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
import com.microcourse.service.StorageApplicationQueryService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.StorageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 15: 微专业申请表 Query Service 实现
 *
 * <p>仅含只读/查询方法，从 {@link StorageApplicationServiceImpl} 中抽取，
 * 职责：获取我的申请列表、详情、预览构建、导出校验、Owner 权限校验。</p>
 */
@Service
public class StorageApplicationQueryServiceImpl implements StorageApplicationQueryService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationQueryServiceImpl.class);

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

    public StorageApplicationQueryServiceImpl(
            MicroSpecialtyProposalRepository proposalRepository,
            ProposalCourseRepository courseRepository,
            ProposalChapterRepository chapterRepository,
            ProposalLeadCourseRepository leadCourseRepository,
            ProposalTeamMemberRepository teamMemberRepository,
            ProposalSignatureRepository signatureRepository,
            ProposalSharedUnitRepository sharedUnitRepository,
            UserRepository userRepository,
            ChapterTeacherAssignmentRepository assignmentRepository,
            DepartmentRepository departmentRepository) {
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
    }

    // ================================================================
    // 2. getMyDrafts
    // ================================================================
    @Override
    public PageResult<StorageApplicationSummaryVO> getMyDrafts(Long userId, int page, int size) {
        // I-05 fix: 如果请求页码越界（page >= totalPages 且 totalPages > 0），自动回退到最后一页
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<MicroSpecialtyProposal>()
                        .eq(MicroSpecialtyProposal::getProposerId, userId)
                        .orderByDesc(MicroSpecialtyProposal::getUpdatedAt));

        if (ipage.getPages() > 0 && page >= ipage.getPages()) {
            int lastPage = (int) ipage.getPages() - 1;
            log.warn("getMyDrafts: page {} out of bounds (totalPages={}), re-querying last page {}", page, ipage.getPages(), lastPage);
            ipage = proposalRepository.selectPage(
                    new Page<>(lastPage + 1, size),
                    new LambdaQueryWrapper<MicroSpecialtyProposal>()
                            .eq(MicroSpecialtyProposal::getProposerId, userId)
                            .orderByDesc(MicroSpecialtyProposal::getUpdatedAt));
            page = lastPage;
        }

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
            // P2-02: 映射自动保存时间
            vo.setLastAutoSavedAt(p.getLastAutoSavedAt());
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
    // P1C-091: 获取待审批列表（ACADEMIC）
    // ================================================================
    @Override
    public PageResult<StorageApplicationSummaryVO> getPendingList(int page, int size) {
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<MicroSpecialtyProposal>()
                        .eq(MicroSpecialtyProposal::getStatus, "PENDING_REVIEW")
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

        Set<Long> proposerIds = proposals.stream()
                .map(MicroSpecialtyProposal::getProposerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> proposerNameMap = new HashMap<>();
        if (!proposerIds.isEmpty()) {
            List<User> users = userRepository.selectBatchIds(proposerIds);
            proposerNameMap = users.stream()
                    .collect(Collectors.toMap(User::getId, User::getRealName));
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
            // P2-02: 映射自动保存时间
            vo.setLastAutoSavedAt(p.getLastAutoSavedAt());
            // 添加申请人信息
            vo.setProposerName(proposerNameMap.getOrDefault(p.getProposerId(), ""));
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

        // AC10: 权限校验 — 本人/ADMIN/ACADEMIC 均可查看
        // ACADEMIC 作为审批人可查看所有申请详情；ADMIN 完全访问
        if (userId != null && !SecurityUtil.isAdminOrAcademic()
                && !proposal.getProposerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return buildVO(proposal);
    }

    // ================================================================
    // validateOwner
    // ================================================================
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
    // validateForExport
    // ================================================================
    @Override
    public ExportValidationResult validateForExport(Long proposalId, Long userId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        StorageApplicationSaveRequest req = buildRequest(proposal);
        List<String> errors;

        // DRAFT 状态：仅做格式校验（手机号/日期格式），不阻断内容完整性
        if ("DRAFT".equals(proposal.getStatus())) {
            errors = StorageValidator.validateFormatOnly(req);
        } else {
            errors = StorageValidator.validateForSubmit(req);
        }

        ExportValidationResult result = new ExportValidationResult();
        for (String error : errors) {
            result.addError(error);
        }

        return result;
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
        // AC10: 允许 ACADEMIC 查看预览（审批人需要查看详情）
        if (!proposal.getProposerId().equals(userId) && !SecurityUtil.isAdminOrAcademic()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        return buildPreview(proposal);
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
            java.time.format.DateTimeFormatter.ofPattern("yyyy.M").format(proposal.getApplyDate()) : null);

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

        // P1-C-2 修复: 填充审核信息
        vo.setReviewComment(proposal.getReviewComment());

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
            java.time.format.DateTimeFormatter.ofPattern("yyyy.M").format(proposal.getApplyDate()) : null);
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

        // P1-C-2 修复: 预览VO也填充reviewComment
        vo.setReviewComment(proposal.getReviewComment());

        return vo;
    }

    @Override
    public StorageApplicationSaveRequest buildValidationRequest(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) {
            throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        }
        return buildRequest(proposal);
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
                java.time.format.DateTimeFormatter.ofPattern("yyyy.M").format(proposal.getApplyDate()) : null);
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
            // 加载该课程的章节
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

        // 加载 SHARED_UNIT 级别签字，按 unitSeq 索引
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

            // 从 proposal_signatures 回填签字数据
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
}

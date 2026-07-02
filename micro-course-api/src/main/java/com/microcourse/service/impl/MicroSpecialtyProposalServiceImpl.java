package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.MicroSpecialtyProposalRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.service.MicroSpecialtyMaterializationService;
import com.microcourse.service.MicroSpecialtyProposalService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;

import java.util.*;
import java.util.stream.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MicroSpecialtyProposalServiceImpl implements MicroSpecialtyProposalService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyProposalServiceImpl.class);

    private final MicroSpecialtyProposalRepository proposalRepository;
    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final NotificationService notificationService;
    private final com.microcourse.repository.DepartmentRepository departmentRepository;
    private final com.microcourse.repository.UserRepository userRepository;
    private final MicroSpecialtyMaterializationService materializationService;

    public MicroSpecialtyProposalServiceImpl(MicroSpecialtyProposalRepository proposalRepository,
                                              MicroSpecialtyRepository msRepository,
                                              MicroSpecialtyTeacherRepository msTeacherRepository,
                                              NotificationService notificationService,
                                              com.microcourse.repository.DepartmentRepository departmentRepository,
                                              com.microcourse.repository.UserRepository userRepository,
                                              MicroSpecialtyMaterializationService materializationService) {
        this.proposalRepository = proposalRepository;
        this.msRepository = msRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.notificationService = notificationService;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.materializationService = materializationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitProposal(MicroSpecialtyProposalRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();

        MicroSpecialtyProposal proposal = new MicroSpecialtyProposal();
        proposal.setProposerId(userId);
        proposal.setTitle(request.getTitle());
        proposal.setDescription(request.getDescription());
        proposal.setOfferDepartmentId(request.getOfferDepartmentId());
        proposal.setTrainingObjective(request.getTrainingObjective());
        proposal.setPrerequisites(request.getPrerequisites());
        proposal.setSemester(request.getSemester());
        proposal.setMaxStudents(request.getMaxStudents());
        if (request.getCredits() != null) proposal.setCredits(request.getCredits());
        proposal.setStatus("PENDING_REVIEW");
        proposal.setCreatedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.insert(proposal);
        return proposal.getId();
    }

    @Override
    public PageResult<?> getMyProposals(int page, int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<MicroSpecialtyProposal>()
                        .eq(MicroSpecialtyProposal::getProposerId, userId)
                        .orderByDesc(MicroSpecialtyProposal::getCreatedAt));
        return enrichWithNames(ipage);
    }

    @Override
    public PageResult<?> getAllPendingProposals(int page, int size, String status) {
        LambdaQueryWrapper<MicroSpecialtyProposal> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !"ALL".equals(status)) {
            wrapper.eq(MicroSpecialtyProposal::getStatus, status);
        } else if (status == null || "ALL".equals(status)) {
        }
        wrapper.orderByDesc(MicroSpecialtyProposal::getCreatedAt);
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size), wrapper);
        return enrichWithNames(ipage);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private PageResult enrichWithNames(IPage<MicroSpecialtyProposal> ipage) {
        if (ipage.getRecords() == null || ipage.getRecords().isEmpty()) {
            return PageResult.of(new java.util.ArrayList(), ipage.getTotal(), 0, (int) ipage.getSize());
        }
        // 批量子表查询学院和用户名称
        java.util.Set<Long> deptIds = new java.util.HashSet<>();
        java.util.Set<Long> userIds = new java.util.HashSet<>();
        for (MicroSpecialtyProposal p : ipage.getRecords()) {
            if (p.getOfferDepartmentId() != null) deptIds.add(p.getOfferDepartmentId());
            if (p.getProposerId() != null) userIds.add(p.getProposerId());
        }
        java.util.Map<Long, String> deptMap = new java.util.HashMap<>();
        if (!deptIds.isEmpty()) {
            List<com.microcourse.entity.Department> depts = departmentRepository.selectBatchIds(deptIds);
            if (depts != null) for (com.microcourse.entity.Department d : depts) deptMap.put(d.getId(), d.getName());
        }
        java.util.Map<Long, String> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            List<com.microcourse.entity.User> users = userRepository.selectBatchIds(userIds);
            if (users != null) for (com.microcourse.entity.User u : users) userMap.put(u.getId(), u.getRealName());
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();
        for (MicroSpecialtyProposal p : ipage.getRecords()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", p.getId());
            map.put("title", p.getTitle());
            map.put("description", p.getDescription());
            map.put("offerDepartmentId", p.getOfferDepartmentId());
            map.put("trainingObjective", p.getTrainingObjective());
            map.put("prerequisites", p.getPrerequisites());
            map.put("semester", p.getSemester());
            map.put("maxStudents", p.getMaxStudents());
            map.put("credits", p.getCredits());
            map.put("status", p.getStatus());
            map.put("type", p.getType());
            map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            map.put("updatedAt", p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
            map.put("version", p.getVersion());
            map.put("collegeName", deptMap.getOrDefault(p.getOfferDepartmentId(), ""));
            map.put("applicantName", userMap.getOrDefault(p.getProposerId(), ""));
            records.add(map);
        }
        return PageResult.of(records, ipage.getTotal(), (int) ipage.getCurrent() - 1, (int) ipage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MicroSpecialtyVO approveProposal(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        if (!"PENDING_REVIEW".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可批准");
        }

        // 更新 proposal 为 APPROVED
        proposal.setStatus("APPROVED");
        proposal.setReviewedBy(SecurityUtil.getCurrentUserId());
        proposal.setReviewedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);

        // 创建微专业 DRAFT（§2.1 路径B）
        MicroSpecialty ms = new MicroSpecialty();
        ms.setCode("MS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ms.setTitle(proposal.getTitle());
        ms.setDescription(proposal.getDescription());
        ms.setOfferDepartmentId(proposal.getOfferDepartmentId());
        ms.setTrainingObjective(proposal.getTrainingObjective());
        ms.setSemester(proposal.getSemester());
        ms.setMaxStudents(proposal.getMaxStudents());
        ms.setLeadTeacherId(proposal.getProposerId());
        ms.setStatus("APPROVED");
        ms.setCreatorId(SecurityUtil.getCurrentUserId());
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        ms.setVersion(0);
        ms.setIsFeatured(false);
        ms.setIsGoldFeatured(false);
        ms.setFeaturedStatus("NONE");
        ms.setStudentCount(0);
        msRepository.insert(ms);

        // 回写 proposal
        proposal.setCreatedMicroSpecialtyId(ms.getId());
        proposalRepository.updateById(proposal);

        // 创建 LEAD INVITED 记录
        MicroSpecialtyTeacher leadRecord = new MicroSpecialtyTeacher();
        leadRecord.setMicroSpecialtyId(ms.getId());
        leadRecord.setTeacherId(proposal.getProposerId());
        leadRecord.setRole("LEAD");
        leadRecord.setInviteStatus("INVITED");
        leadRecord.setInvitedBy(SecurityUtil.getCurrentUserId());
        leadRecord.setInvitedAt(LocalDateTime.now());
        leadRecord.setInviteExpiresAt(LocalDateTime.now().plusDays(7));
        leadRecord.setCreatedAt(LocalDateTime.now());
        msTeacherRepository.insert(leadRecord);

        // 通知申报人
        notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_PROPOSAL_APPROVED,
                "申报已批准", "您的微专业申报《" + proposal.getTitle() + "》已获批准，请接受负责人邀请", ms.getId());

        // 通知 LEAD
        notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_INVITE_LEAD,
                "微专业负责人邀请", "您已被指定为微专业《" + ms.getTitle() + "》负责人，请在7天内接受邀请", ms.getId());

        MicroSpecialtyVO vo = new MicroSpecialtyVO();
        vo.setId(ms.getId());
        vo.setCode(ms.getCode());
        vo.setTitle(ms.getTitle());
        vo.setStatus(ms.getStatus());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectProposal(Long proposalId, String reason) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        if (!"PENDING_REVIEW".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可驳回");
        }

        proposal.setStatus("REJECTED");
        proposal.setReviewComment(reason);
        proposal.setReviewedBy(SecurityUtil.getCurrentUserId());
        proposal.setReviewedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);

        notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_PROPOSAL_REJECTED,
                "申报被驳回", "您的微专业申报《" + proposal.getTitle() + "》被驳回，原因：" + (reason != null ? reason : "未填写"), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawProposal(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!proposal.getProposerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"PENDING_REVIEW".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可撤回");
        }

        proposal.setStatus("WITHDRAWN");
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitProposal(Long proposalId, MicroSpecialtyProposalRequest request) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!proposal.getProposerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"REJECTED".equals(proposal.getStatus()) && !"WITHDRAWN".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已驳回或已撤回状态可重提");
        }

        if (request.getTitle() != null) proposal.setTitle(request.getTitle());
        if (request.getDescription() != null) proposal.setDescription(request.getDescription());
        if (request.getTrainingObjective() != null) proposal.setTrainingObjective(request.getTrainingObjective());
        if (request.getPrerequisites() != null) proposal.setPrerequisites(request.getPrerequisites());
        if (request.getSemester() != null) proposal.setSemester(request.getSemester());
        if (request.getMaxStudents() != null) proposal.setMaxStudents(request.getMaxStudents());
        if (request.getOfferDepartmentId() != null) proposal.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getCredits() != null) proposal.setCredits(request.getCredits());

        proposal.setStatus("PENDING_REVIEW");
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);
    }

    @Override
    public MicroSpecialtyProposalRequest getProposal(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!proposal.getProposerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        MicroSpecialtyProposalRequest vo = new MicroSpecialtyProposalRequest();
        vo.setTitle(proposal.getTitle());
        vo.setDescription(proposal.getDescription());
        vo.setTrainingObjective(proposal.getTrainingObjective());
        vo.setPrerequisites(proposal.getPrerequisites());
        vo.setSemester(proposal.getSemester());
        vo.setMaxStudents(proposal.getMaxStudents());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProposal(Long proposalId, MicroSpecialtyProposalRequest request) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!proposal.getProposerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"WITHDRAWN".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已撤回状态可编辑");
        }

        if (request.getTitle() != null) proposal.setTitle(request.getTitle());
        if (request.getDescription() != null) proposal.setDescription(request.getDescription());
        if (request.getTrainingObjective() != null) proposal.setTrainingObjective(request.getTrainingObjective());
        if (request.getPrerequisites() != null) proposal.setPrerequisites(request.getPrerequisites());
        if (request.getSemester() != null) proposal.setSemester(request.getSemester());
        if (request.getMaxStudents() != null) proposal.setMaxStudents(request.getMaxStudents());
        if (request.getOfferDepartmentId() != null) proposal.setOfferDepartmentId(request.getOfferDepartmentId());
        if (request.getCredits() != null) proposal.setCredits(request.getCredits());

        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProposal(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!proposal.getProposerId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"DRAFT".equals(proposal.getStatus()) && !"WITHDRAWN".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅草稿或已撤回状态可删除");
        }

        proposalRepository.deleteById(proposalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveAndCreateSpecialty(Long proposalId, Long reviewerId) {
        MicroSpecialtyProposal proposal = proposalRepository.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.MS_PROPOSAL_NOT_FOUND);

        if (!"PENDING_REVIEW".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可批准");
        }

        // 更新 proposal 为 APPROVED
        proposal.setStatus("APPROVED");
        proposal.setReviewedBy(reviewerId);
        proposal.setReviewedAt(LocalDateTime.now());
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepository.updateById(proposal);

        // 创建微专业 DRAFT
        MicroSpecialty ms = new MicroSpecialty();
        ms.setCode("MS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());

        // 对于 storage 类型，使用 Phase 15 新增字段
        if (proposal.getTitle() != null) {
            ms.setTitle(proposal.getTitle());
        } else {
            ms.setTitle(proposal.getLeadName() != null ? proposal.getLeadName() + "的微专业" : "未命名微专业");
        }
        ms.setDescription(proposal.getDescription() != null ? proposal.getDescription() : proposal.getIntroduction());
        ms.setOfferDepartmentId(proposal.getOfferDepartmentId());
        ms.setTrainingObjective(proposal.getTrainingObjective() != null ? proposal.getTrainingObjective() :
            (proposal.getSpecialtyOverview() != null ? proposal.getSpecialtyOverview() : proposal.getMarketDemandAnalysis()));
        // storage 端用 enrollmentQuota+classSize 取代 maxStudents
        ms.setSemester(proposal.getSemester() != null ? proposal.getSemester() :
            (proposal.getStartDate() != null ? String.valueOf(proposal.getStartDate().getYear()) : null));
        ms.setMaxStudents(proposal.getMaxStudents() != null ? proposal.getMaxStudents() :
            (proposal.getEnrollmentQuota() != null ? proposal.getEnrollmentQuota() :
             proposal.getClassSize() != null ? proposal.getClassSize() : 0));
        ms.setLeadTeacherId(proposal.getProposerId());
        ms.setStatus("APPROVED");
        ms.setCreatorId(reviewerId);
        ms.setCreatedAt(LocalDateTime.now());
        ms.setUpdatedAt(LocalDateTime.now());
        ms.setVersion(0);
        ms.setIsFeatured(false);
        ms.setIsGoldFeatured(false);
        ms.setFeaturedStatus("NONE");
        ms.setStudentCount(0);
        msRepository.insert(ms);

        // 回写 proposal
        proposal.setCreatedMicroSpecialtyId(ms.getId());
        proposalRepository.updateById(proposal);

        // 创建 LEAD INVITED 记录
        MicroSpecialtyTeacher leadRecord = new MicroSpecialtyTeacher();
        leadRecord.setMicroSpecialtyId(ms.getId());
        leadRecord.setTeacherId(proposal.getProposerId());
        leadRecord.setRole("LEAD");
        leadRecord.setInviteStatus("INVITED");
        leadRecord.setInvitedBy(reviewerId);
        leadRecord.setInvitedAt(LocalDateTime.now());
        leadRecord.setInviteExpiresAt(LocalDateTime.now().plusDays(7));
        leadRecord.setCreatedAt(LocalDateTime.now());
        msTeacherRepository.insert(leadRecord);

        // 通知申报人
        notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_PROPOSAL_APPROVED,
                "申报已批准", "您的微专业申报已获批准，请接受负责人邀请", ms.getId());

        // 通知 LEAD
        notificationService.notifyAsync(proposal.getProposerId(), NotificationType.MS_INVITE_LEAD,
                "微专业负责人邀请", "您已被指定为微专业《" + ms.getTitle() + "》负责人，请在7天内接受邀请", ms.getId());

        log.info("approveAndCreateSpecialty: proposalId={}, msId={}", proposalId, ms.getId());

        // Phase 5: 物化申报中的章节到微专业
        try {
            materializationService.materialize(proposalId);
        } catch (Exception e) {
            log.error("materialize failed for proposal {}", proposalId, e);
            // 不阻断审批流程, 但记录错误供后续排查
        }
    }
}

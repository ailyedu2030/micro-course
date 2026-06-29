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
import com.microcourse.service.MicroSpecialtyProposalService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
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

    public MicroSpecialtyProposalServiceImpl(MicroSpecialtyProposalRepository proposalRepository,
                                             MicroSpecialtyRepository msRepository,
                                             MicroSpecialtyTeacherRepository msTeacherRepository,
                                             NotificationService notificationService) {
        this.proposalRepository = proposalRepository;
        this.msRepository = msRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.notificationService = notificationService;
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
        return PageResult.of(ipage);
    }

    @Override
    public PageResult<?> getAllPendingProposals(int page, int size, String status) {
        LambdaQueryWrapper<MicroSpecialtyProposal> wrapper = new LambdaQueryWrapper<>();
        // status=null 或 "ALL" 表示不按状态过滤，显示全部
        if (status != null && !"ALL".equals(status)) {
            wrapper.eq(MicroSpecialtyProposal::getStatus, status);
        } else if (status == null || "ALL".equals(status)) {
            // 不加 status 过滤条件
        }
        wrapper.orderByDesc(MicroSpecialtyProposal::getCreatedAt);
        IPage<MicroSpecialtyProposal> ipage = proposalRepository.selectPage(
                new Page<>(page + 1, size), wrapper);
        return PageResult.of(ipage);
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

        if (!"REJECTED".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已驳回状态可重提");
        }

        if (request.getTitle() != null) proposal.setTitle(request.getTitle());
        if (request.getDescription() != null) proposal.setDescription(request.getDescription());
        if (request.getTrainingObjective() != null) proposal.setTrainingObjective(request.getTrainingObjective());
        if (request.getPrerequisites() != null) proposal.setPrerequisites(request.getPrerequisites());
        if (request.getSemester() != null) proposal.setSemester(request.getSemester());
        if (request.getMaxStudents() != null) proposal.setMaxStudents(request.getMaxStudents());

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

        if (!"WITHDRAWN".equals(proposal.getStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已撤回状态可删除");
        }

        proposalRepository.deleteById(proposalId);
    }
}

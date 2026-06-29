package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyProposalRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;

/**
 * 微专业申报 Service 接口。
 * 职责：教师申报 + 审批 + 撤回 + 重提。
 */
public interface MicroSpecialtyProposalService {

    /** 教师提交申报 */
    Long submitProposal(MicroSpecialtyProposalRequest request);

    /** 我的申报列表 */
    PageResult<?> getMyProposals(int page, int size);

    /** 所有待审申报（ACADEMIC，status=null/ALL 表示全部状态） */
    PageResult<?> getAllPendingProposals(int page, int size, String status);

    /** 批准申报 → 创建 DRAFT + LEAD INVITED */
    MicroSpecialtyVO approveProposal(Long proposalId);

    /** 驳回申报（填写原因） */
    void rejectProposal(Long proposalId, String reason);

    /** 撤回申报（仅 PENDING_REVIEW 状态，本人操作） */
    void withdrawProposal(Long proposalId);

    /** 重提申报（REJECTED → PENDING_REVIEW，本人操作） */
    void resubmitProposal(Long proposalId, MicroSpecialtyProposalRequest request);

    /** 获取申报详情 */
    MicroSpecialtyProposalRequest getProposal(Long proposalId);

    /** 编辑申报（仅 WITHDRAWN 状态） */
    void updateProposal(Long proposalId, MicroSpecialtyProposalRequest request);

    /** 删除申报（仅 WITHDRAWN 状态） */
    void deleteProposal(Long proposalId);
}

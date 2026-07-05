package com.microcourse.service;

import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;
import com.microcourse.entity.MicroSpecialty;

/**
 * 微专业管理 Service（提取自 MicroSpecialtyServiceImpl 的状态流转 + 角色鉴权）。
 * <p>
 * 职责：状态流转（submit / approve / reject / open / close / cancel / archive）、
 * LEAD 继任（transferLeadership）、角色鉴权（requireLeadOf / requireOwnerOrLead / checkNotTerminal）。
 * 与 MicroSpecialtyQueryService 互补，共同为 MicroSpecialtyServiceImpl 瘦身。
 * </p>
 */
public interface MicroSpecialtyAdminService {

    /** 校验当前用户是否为微专业负责人或系统管理员，否则抛出业务异常 */
    void requireLeadOf(Long msId);

    /** 校验当前用户是否为微专业负责人/创建者或系统管理员，否则抛出业务异常 */
    void requireOwnerOrLead(Long msId);

    /** 校验微专业是否为终态（CANCELLED/ARCHIVED），终态不允许教师邀请/移除操作 */
    void checkNotTerminal(MicroSpecialty ms);

    // ====== 状态流转 ======

    /** 提交/重新提交审核：DRAFT/REJECTED → PENDING_REVIEW */
    void submit(Long id);

    /** 教务处审批通过：PENDING_REVIEW → APPROVED */
    void approve(Long id);

    /** 教务处审批驳回：PENDING_REVIEW → REJECTED */
    void reject(Long id, String reason);

    /** LEAD 开课：APPROVED → RECRUITING */
    void open(Long id);

    /** LEAD 结业：RECRUITING → COMPLETED */
    void close(Long id);

    /** 教务处强制取消：任意状态 → CANCELLED（终态），事务内级联清理 */
    void cancel(Long id, String reason);

    /** 归档：COMPLETED → ARCHIVED */
    void archive(Long id);

    /** P2-11: 批量审批通过 */
    BatchOperationResult batchApprove(java.util.List<Long> ids);

    /** P2-11: 批量审批驳回 */
    BatchOperationResult batchReject(java.util.List<Long> ids, String reason);

    // ====== LEAD 继任 ======

    /** 教务处发起 LEAD 继任：指定新 LEAD，原 LEAD 降为 MEMBER */
    void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request);
}

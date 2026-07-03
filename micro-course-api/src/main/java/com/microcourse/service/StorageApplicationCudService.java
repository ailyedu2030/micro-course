package com.microcourse.service;

import com.microcourse.dto.storage.StorageApplicationSaveRequest;
import com.microcourse.entity.MicroSpecialtyProposal;

/**
 * Phase 15: 微专业申请表 CUD Service 接口
 *
 * <p>仅含写操作辅助方法，从 {@link StorageApplicationService} 中抽取，
 * 职责：请求到实体的字段映射、子表先删后插替换。</p>
 */
public interface StorageApplicationCudService {

    /**
     * 对标 ProposalCourseItem 字段，将请求对象映射到主表 Entity
     */
    void applyRequestToProposal(MicroSpecialtyProposal proposal, StorageApplicationSaveRequest request);

    /**
     * 先删后插替换子表数据。
     * 事务保护由调用方 @Transactional 保证，本方法要求 Propagation.MANDATORY。
     */
    void replaceSubTables(Long proposalId, StorageApplicationSaveRequest request, boolean includeSharedUnits);
}

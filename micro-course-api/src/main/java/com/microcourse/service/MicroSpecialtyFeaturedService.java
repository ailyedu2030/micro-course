package com.microcourse.service;

/**
 * 微专业置顶/金标 Service 接口。
 * 职责：置顶申请/审批 + 金标管理。
 */
public interface MicroSpecialtyFeaturedService {

    /** 申请置顶（LEAD）→ PENDING */
    void applyFeatured(Long msId, String reason);

    /** 批准置顶（ACADEMIC）→ APPROVED */
    void approveFeatured(Long msId);

    /** 驳回置顶（ACADEMIC）→ REJECTED */
    void rejectFeatured(Long msId, String reason);

    /** 取消置顶（ACADEMIC）APPROVED → NONE */
    void unsetFeatured(Long msId);

    /** 设置金标（ACADEMIC）：全校最多 2 个 */
    void setGoldFeatured(Long msId);

    /** 取消金标（ACADEMIC） */
    void unsetGoldFeatured(Long msId);
}

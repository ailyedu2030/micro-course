package com.microcourse.service;

import com.microcourse.dto.PageResult;

/**
 * 微专业教师邀请 Service 接口。
 * 职责：待处理邀请 + 接受/拒绝 + 跨学院审批 + 过期扫描。
 */
public interface MicroSpecialtyInviteService {

    /** 我的待处理邀请列表 */
    PageResult<?> getPendingInvites(int page, int size);

    /** 接受邀请 → ACTIVE 或 PENDING_ACADEMIC（跨学院） */
    void acceptInvite(Long inviteId);

    /** 拒绝邀请 → DECLINED */
    void declineInvite(Long inviteId);

    /** 主动退出团队 → REMOVED */
    void leaveTeam(Long msId);

    /** 跨学院审批（ACADEMIC） */
    void reviewCrossDept(Long inviteId, boolean approve, String reason);

    /** 重新邀请（复用 REMOVED/DECLINED 记录重置状态） */
    /** 重新邀请（§7.4 端点对齐 spec：用 inviteId 复用 DECLINED/REMOVED 记录） */
    void reinviteTeacher(Long inviteId, String role, String responsibility, Long courseId);

    /** 每小时扫 INVITED 过期 → DECLINED + 通知 */
    int scanExpired();
}

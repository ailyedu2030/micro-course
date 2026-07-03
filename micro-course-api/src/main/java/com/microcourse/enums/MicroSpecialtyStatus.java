package com.microcourse.enums;

public enum MicroSpecialtyStatus {
    DRAFT(0, "草稿"),
    PENDING_REVIEW(1, "待审核"),
    APPROVED(2, "已通过"),
    REJECTED(7, "已驳回"),
    RECRUITING(3, "招生中"),
    COMPLETED(4, "已完成"),
    CANCELLED(5, "已取消"),
    ARCHIVED(6, "已归档");

    private final int code;
    private final String description;

    MicroSpecialtyStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyStatus fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * ★ 业务逻辑审计 P2-2 修复：微专业主表状态机集中白名单。
     * <p>对齐 docs/开发规划/phase14-micro-specialty-spec.md §2.1 状态机：</p>
     * <ul>
     *   <li>DRAFT → PENDING_REVIEW（LEAD 提交审核）</li>
     *   <li>PENDING_REVIEW → APPROVED / REJECTED（ACADEMIC 审批）</li>
     *   <li>REJECTED → DRAFT / PENDING_REVIEW（重提）</li>
     *   <li>APPROVED → RECRUITING（招生）</li>
     *   <li>RECRUITING → COMPLETED（结业）</li>
     *   <li>COMPLETED → ARCHIVED（归档）</li>
     *   <li>任意 → CANCELLED（教务处强制）</li>
     *   <li>终态：CANCELLED / ARCHIVED</li>
     * </ul>
     */
    public boolean canTransitionTo(MicroSpecialtyStatus target) {
        if (target == null || target == this) {
            return false;
        }
        // CANCELLED 可从任何非终态转换
        if (target == CANCELLED) {
            return this != CANCELLED && this != ARCHIVED;
        }
        switch (this) {
            case DRAFT:
                return target == PENDING_REVIEW;
            case PENDING_REVIEW:
                return target == APPROVED || target == REJECTED;
            case REJECTED:
                return target == DRAFT || target == PENDING_REVIEW;
            case APPROVED:
                return target == RECRUITING;
            case RECRUITING:
                return target == COMPLETED;
            case COMPLETED:
                return target == ARCHIVED;
            case CANCELLED:  // 终态
            case ARCHIVED:   // 终态
            default:
                return false;
        }
    }
}

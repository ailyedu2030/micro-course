package com.microcourse.enums;

/**
 * 微专业申报状态枚举（对齐 micro_specialty_proposals.status 数据字典）。
 *
 * <p>状态机转换规则：</p>
 * <ul>
 *   <li>DRAFT → PENDING_REVIEW（提交审核）</li>
 *   <li>PENDING_REVIEW → APPROVED（审批通过）/ REJECTED（驳回）/ WITHDRAWN（撤回）</li>
 *   <li>REJECTED → DRAFT / PENDING_REVIEW（重提）</li>
 *   <li>WITHDRAWN → DRAFT（编辑后重提）</li>
 *   <li>APPROVED → 终态</li>
 * </ul>
 */
public enum MicroSpecialtyProposalStatus {

    DRAFT("DRAFT"),
    PENDING_REVIEW("PENDING_REVIEW"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    WITHDRAWN("WITHDRAWN");

    private final String value;

    MicroSpecialtyProposalStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MicroSpecialtyProposalStatus fromString(String s) {
        if (s == null) return null;
        for (MicroSpecialtyProposalStatus status : values()) {
            if (status.value.equalsIgnoreCase(s) || status.name().equalsIgnoreCase(s)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 状态机转换白名单。
     *
     * @param target 目标状态
     * @return 是否允许从当前状态转换到 target
     */
    public boolean canTransitionTo(MicroSpecialtyProposalStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case DRAFT:
                return target == PENDING_REVIEW;
            case PENDING_REVIEW:
                return target == APPROVED || target == REJECTED || target == WITHDRAWN;
            case REJECTED:
                return target == DRAFT || target == PENDING_REVIEW;
            case WITHDRAWN:
                return target == DRAFT;
            case APPROVED:  // 终态
            default:
                return false;
        }
    }
}

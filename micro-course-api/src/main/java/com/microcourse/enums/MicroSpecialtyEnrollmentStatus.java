package com.microcourse.enums;

public enum MicroSpecialtyEnrollmentStatus {
    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    IN_PROGRESS(2, "进行中"),
    COMPLETED(3, "已完成"),
    DROPPED(4, "已退课"),
    FAILED(5, "未通过"),
    REJECTED(6, "已驳回"),
    CERTIFIED(7, "已颁证");

    private final int code;
    private final String description;

    MicroSpecialtyEnrollmentStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyEnrollmentStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyEnrollmentStatus fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyEnrollmentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * ★ 业务逻辑审计 P2-2 修复：微专业修读状态机集中白名单。
     * <p>对齐 docs/开发规划/phase14-micro-specialty-spec.md §2.2 状态机：</p>
     * <ul>
     *   <li>PENDING → APPROVED / REJECTED</li>
     *   <li>APPROVED → IN_PROGRESS（开课）/ DROPPED（学生退）</li>
     *   <li>IN_PROGRESS → COMPLETED / FAILED / DROPPED</li>
     *   <li>COMPLETED → CERTIFIED（颁证）</li>
     *   <li>终态：REJECTED / FAILED / DROPPED / CERTIFIED</li>
     * </ul>
     */
    public boolean canTransitionTo(MicroSpecialtyEnrollmentStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == APPROVED || target == REJECTED;
            case APPROVED:
                return target == IN_PROGRESS || target == DROPPED;
            case IN_PROGRESS:
                return target == COMPLETED || target == FAILED || target == DROPPED;
            case COMPLETED:
                return target == CERTIFIED;
            case REJECTED:   // 终态
            case FAILED:     // 终态
            case DROPPED:    // 终态
            case CERTIFIED:  // 终态
            default:
                return false;
        }
    }
}

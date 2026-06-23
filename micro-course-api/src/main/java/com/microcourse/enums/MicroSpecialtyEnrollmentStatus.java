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
}

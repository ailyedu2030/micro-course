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
}

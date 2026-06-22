package com.microcourse.enums;

public enum MicroSpecialtyFeaturedStatus {
    NONE(0, "未申请"),
    PENDING(1, "待审核"),
    APPROVED(2, "已通过"),
    REJECTED(3, "已驳回");

    private final int code;
    private final String description;

    MicroSpecialtyFeaturedStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyFeaturedStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyFeaturedStatus fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyFeaturedStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}

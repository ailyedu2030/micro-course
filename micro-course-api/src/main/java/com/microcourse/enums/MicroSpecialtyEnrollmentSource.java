package com.microcourse.enums;

public enum MicroSpecialtyEnrollmentSource {
    SELF_APPLY(0, "自主报名"),
    CLASS_IMPORT(1, "班级导入"),
    ADMIN_ASSIGN(2, "管理员分配");

    private final int code;
    private final String description;

    MicroSpecialtyEnrollmentSource(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyEnrollmentSource source : values()) {
            if (source.code == code) {
                return source.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyEnrollmentSource fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyEnrollmentSource source : values()) {
            if (source.code == code) {
                return source;
            }
        }
        return null;
    }
}

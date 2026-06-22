package com.microcourse.enums;

public enum MicroSpecialtyTeacherRole {
    LEAD(0, "负责人"),
    MEMBER(1, "成员"),
    ASSISTANT(2, "助教");

    private final int code;
    private final String description;

    MicroSpecialtyTeacherRole(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyTeacherRole role : values()) {
            if (role.code == code) {
                return role.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyTeacherRole fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyTeacherRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }
}

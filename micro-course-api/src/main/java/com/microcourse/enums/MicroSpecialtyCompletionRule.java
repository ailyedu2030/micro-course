package com.microcourse.enums;

public enum MicroSpecialtyCompletionRule {
    ALL_REQUIRED(0, "必修课全部完成"),
    CREDITS_MIN(1, "学分达标"),
    MIXED(2, "混合方式");

    private final int code;
    private final String description;

    MicroSpecialtyCompletionRule(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyCompletionRule rule : values()) {
            if (rule.code == code) {
                return rule.description;
            }
        }
        return null;
    }

    public static MicroSpecialtyCompletionRule fromCode(Integer code) {
        if (code == null) return null;
        for (MicroSpecialtyCompletionRule rule : values()) {
            if (rule.code == code) {
                return rule;
            }
        }
        return null;
    }
}

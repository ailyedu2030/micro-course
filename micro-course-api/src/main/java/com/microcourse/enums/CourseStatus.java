package com.microcourse.enums;

public enum CourseStatus {
    DRAFT(0, "草稿"),
    PENDING_REVIEW(1, "待审核"),
    APPROVED(2, "通过"),
    REJECTED(3, "驳回"),
    PUBLISHED(4, "已发布"),
    CLOSED(5, "下架"),
    ARCHIVED(6, "归档");

    private final int code;
    private final String description;

    CourseStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static String getDescription(Integer code) {
        if (code == null) return null;
        for (CourseStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }
        return null;
    }
}
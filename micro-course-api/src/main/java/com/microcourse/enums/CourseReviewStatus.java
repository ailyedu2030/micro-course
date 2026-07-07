package com.microcourse.enums;

public enum CourseReviewStatus {
    PENDING(0, "待审核"),
    APPROVED(1, "通过"),
    REJECTED(2, "驳回");

    private final int code;
    private final String label;

    CourseReviewStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public boolean canTransitionTo(CourseReviewStatus target) {
        if (target == null || target == this) return false;
        switch (this) {
            case PENDING:  return target == APPROVED || target == REJECTED;
            case APPROVED: return target == REJECTED;
            case REJECTED: return false;
            default:       return false;
        }
    }

    public static CourseReviewStatus fromCode(Integer code) {
        if (code == null) return null;
        for (CourseReviewStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
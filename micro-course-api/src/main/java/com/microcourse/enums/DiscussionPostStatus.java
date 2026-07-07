package com.microcourse.enums;

public enum DiscussionPostStatus {
    PENDING(0, "待审核"),
    PUBLISHED(1, "已发布"),
    REJECTED(2, "已驳回"),
    DELETED(3, "已删除");

    private final int code;
    private final String label;

    DiscussionPostStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() { return code; }
    public String getLabel() { return label; }

    public boolean canTransitionTo(DiscussionPostStatus target) {
        if (target == null || target == this) return false;
        switch (this) {
            case PENDING:   return target == PUBLISHED || target == REJECTED;
            case PUBLISHED: return target == REJECTED || target == DELETED;
            case REJECTED:  return target == PENDING;
            case DELETED:   return false;
            default:        return false;
        }
    }

    public static DiscussionPostStatus fromCode(Integer code) {
        if (code == null) return null;
        for (DiscussionPostStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}
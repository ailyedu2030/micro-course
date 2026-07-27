package com.microcourse.enums;

/**
 * 讨论区评论状态枚举。
 *
 * <p>状态值：</p>
 * <ul>
 *   <li>1 = PUBLISHED（已发布）</li>
 *   <li>2 = HIDDEN（已隐藏）</li>
 * </ul>
 */
public enum DiscussionCommentStatus {

    PUBLISHED(1, "PUBLISHED"),
    HIDDEN(2, "HIDDEN");

    private final int code;
    private final String label;

    DiscussionCommentStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static DiscussionCommentStatus fromCode(Integer code) {
        if (code == null) return null;
        for (DiscussionCommentStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}

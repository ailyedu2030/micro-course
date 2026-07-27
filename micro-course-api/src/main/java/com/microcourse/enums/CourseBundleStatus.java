package com.microcourse.enums;

/**
 * 课程包状态枚举。
 *
 * <p>状态值：</p>
 * <ul>
 *   <li>0 = DRAFT（草稿）</li>
 *   <li>1 = PUBLISHED（已发布）</li>
 *   <li>2 = ARCHIVED（已归档）</li>
 * </ul>
 */
public enum CourseBundleStatus {

    DRAFT(0, "DRAFT"),
    PUBLISHED(1, "PUBLISHED"),
    ARCHIVED(2, "ARCHIVED");

    private final int code;
    private final String label;

    CourseBundleStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static CourseBundleStatus fromCode(Integer code) {
        if (code == null) return null;
        for (CourseBundleStatus s : values()) {
            if (s.code == code) return s;
        }
        return null;
    }
}

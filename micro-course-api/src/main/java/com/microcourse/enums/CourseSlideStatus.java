package com.microcourse.enums;

/**
 * 课件文件状态枚举（对齐 docs/状态机设计.md §7.3）。
 *
 * <p>状态机：UPLOADING → TRANSCODING → COMPLETED / FAILED</p>
 */
public enum CourseSlideStatus {

    UPLOADING(0, "上传中"),
    TRANSCODING(1, "转码中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "转码失败");

    private final int code;
    private final String label;

    CourseSlideStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 状态转换白名单（对齐 docs/状态机设计.md §7.3 状态转换图）。
     *
     * <ul>
     *   <li>UPLOADING → TRANSCODING / FAILED</li>
     *   <li>TRANSCODING → COMPLETED / FAILED</li>
     *   <li>FAILED → TRANSCODING（重试）</li>
     *   <li>COMPLETED = 终态</li>
     * </ul>
     */
    public boolean canTransitionTo(CourseSlideStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case UPLOADING:
                return target == TRANSCODING || target == FAILED;
            case TRANSCODING:
                return target == COMPLETED || target == FAILED;
            case FAILED:
                return target == TRANSCODING;
            case COMPLETED:  // 终态
            default:
                return false;
        }
    }

    /**
     * 容错解析：把状态码归一为枚举。
     */
    public static CourseSlideStatus fromCode(int code) {
        for (CourseSlideStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知课件状态码: " + code);
    }
}

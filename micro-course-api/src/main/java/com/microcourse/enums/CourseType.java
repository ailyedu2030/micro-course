package com.microcourse.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 课程类型枚举（V333 简化方案 · 4 值）。
 *
 * <p>HTML 课件 + PPT 课件 2 种课件类型独立管理，VIDEO / OFFLINE 保留旧值。</p>
 * <p>旧值 INTERACTIVE 不作为枚举值保留（约束：不保留 INTERACTIVE 枚举），
 * 仅在 {@link #normalize(String)} 中做字符串级向后兼容 —— V333 迁移后 DB 中已无 INTERACTIVE 行，
 * 此处兜底处理旧前端/旧缓存仍发送 INTERACTIVE 的请求。</p>
 */
public enum CourseType {
    HTML_COURSEWARE("HTML_COURSEWARE", "HTML 课件"),
    PPT_COURSEWARE("PPT_COURSEWARE", "PPT 课件"),

    // 旧值兼容（displayName 为 null → 回退显示 code）
    VIDEO("VIDEO", null),
    OFFLINE("OFFLINE", null);

    private final String code;
    private final String displayName;

    CourseType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName != null ? displayName : code; }

    /**
     * 归一化：旧值 INTERACTIVE → HTML_COURSEWARE；未知值返回 null。
     */
    public static CourseType normalize(String raw) {
        if (raw == null) return null;
        Optional<CourseType> direct = Arrays.stream(values())
            .filter(t -> t.code.equals(raw)).findFirst();
        if (direct.isPresent()) return direct.get();
        switch (raw) {
            case "VIDEO": return VIDEO;
            case "INTERACTIVE": return HTML_COURSEWARE;
            case "OFFLINE": return OFFLINE;
            default: return null;
        }
    }

    public static boolean isValid(String code) {
        return normalize(code) != null;
    }

    /**
     * 是否为课件类课程类型（HTML 课件 / PPT 课件）——需要 interactive 插件授权 + 课件就绪守卫。
     */
    public static boolean isCoursewareType(CourseType type) {
        return type == HTML_COURSEWARE || type == PPT_COURSEWARE;
    }

    public static boolean isCoursewareType(String code) {
        return isCoursewareType(normalize(code));
    }
}

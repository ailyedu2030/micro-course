package com.microcourse.service.impl;

import java.util.List;

/**
 * 微专业 pendingCourses JSON 序列化工具。
 * 从 MicroSpecialtyEnrollmentServiceImpl 中提取，以控制文件行数不超过 800 行限制。
 */
public final class PendingCourseJsonUtil {

    /** pendingCourses 内部数据结构（DTO 序列化为 JSON） */
    public static class PendingCourseItem {
        public Long courseId;
        public String courseName;
        public String reason;

        public PendingCourseItem(Long courseId, String courseName, String reason) {
            this.courseId = courseId;
            this.courseName = courseName;
            this.reason = reason;
        }
    }

    /** List<PendingCourseItem> → JSON 字符串（轻量手写避免引入 Jackson 依赖问题） */
    public static String toPendingJson(List<PendingCourseItem> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            PendingCourseItem p = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"courseId\":").append(p.courseId)
              .append(",\"courseName\":").append(jsonEscape(p.courseName))
              .append(",\"reason\":").append(jsonEscape(p.reason)).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private PendingCourseJsonUtil() {}
}

package com.microcourse.enums;

/**
 * 通知类型枚举（P0-7 通知矩阵接线）。
 *
 * <p>code 直接持久化进 notifications.type 列（VARCHAR(30)，无 CHECK 约束）。
 * 业务事件触发通知时统一引用本枚举，避免硬编码字符串。
 *
 * <p>注意：DB schema 未变更，仅约定 type 取值集合；前端可依据 type + relatedId 拼装跳转链接。
 */
public enum NotificationType {

    ENROLLMENT_SUCCESS("ENROLLMENT_SUCCESS", "选课成功"),
    EXERCISE_GRADED("EXERCISE_GRADED", "作业已批改"),
    COURSE_APPROVED("COURSE_APPROVED", "课程已通过"),
    COURSE_REJECTED("COURSE_REJECTED", "课程被驳回"),
    COURSE_PUBLISHED("COURSE_PUBLISHED", "课程已上架"),
    VIDEO_TRANSCODED("VIDEO_TRANSCODED", "视频转码完成"),
    DISCUSSION_REPLY("DISCUSSION_REPLY", "讨论区有新回复");

    private final String code;
    private final String label;

    NotificationType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 持久化到 notifications.type 的值 */
    public String getCode() {
        return code;
    }

    /** 人类可读标签 */
    public String getLabel() {
        return label;
    }
}

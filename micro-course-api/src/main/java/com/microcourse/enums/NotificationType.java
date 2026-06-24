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
    ENROLLMENT_WAITLIST("ENROLLMENT_WAITLIST", "已进入候补队列"),
    EXERCISE_GRADED("EXERCISE_GRADED", "作业已批改"),
    COURSE_APPROVED("COURSE_APPROVED", "课程已通过"),
    COURSE_REJECTED("COURSE_REJECTED", "课程被驳回"),
    COURSE_PUBLISHED("COURSE_PUBLISHED", "课程已上架"),
    VIDEO_TRANSCODED("VIDEO_TRANSCODED", "视频转码完成"),
    DISCUSSION_REPLY("DISCUSSION_REPLY", "讨论区有新回复"),

    // Phase 14: 微专业通知（23 种）
    MS_INVITE_LEAD("MS_INVITE_LEAD", "微专业负责人邀请"),
    MS_INVITE_TEAM("MS_INVITE_TEAM", "微专业团队邀请"),
    MS_INVITE_ACCEPTED("MS_INVITE_ACCEPTED", "邀请已被接受"),
    MS_INVITE_EXPIRED("MS_INVITE_EXPIRED", "邀请已过期"),
    MS_INVITE_CROSS_DEPT("MS_INVITE_CROSS_DEPT", "跨学院邀请待审批"),
    MS_PROPOSAL_APPROVED("MS_PROPOSAL_APPROVED", "申报已批准"),
    MS_PROPOSAL_REJECTED("MS_PROPOSAL_REJECTED", "申报被驳回"),
    MS_SUBMITTED("MS_SUBMITTED", "微专业待审核"),
    MS_REJECTED("MS_REJECTED", "微专业审核被驳回"),
    MS_APPROVED("MS_APPROVED", "微专业审核通过"),
    MS_FEATURED_APPROVED("MS_FEATURED_APPROVED", "置顶审批通过"),
    MS_FEATURED_REJECTED("MS_FEATURED_REJECTED", "置顶审批驳回"),
    MS_OPENED("MS_OPENED", "微专业已开课"),
    MS_ENROLLMENT_APPROVED("MS_ENROLLMENT_APPROVED", "报名已通过"),
    MS_ENROLLMENT_REJECTED("MS_ENROLLMENT_REJECTED", "报名被驳回"),
    MS_ENROLLMENT_AUTO_ENROLL("MS_ENROLLMENT_AUTO_ENROLL", "已批量导入微专业"),
    MS_ENROLLMENT_PENDING("MS_ENROLLMENT_PENDING", "学生报名待审核"),
    MS_ENROLLMENT_DROPPED("MS_ENROLLMENT_DROPPED", "学生已退出微专业"),
    MS_ENROLLMENT_REAPPLIED("MS_ENROLLMENT_REAPPLIED", "重新申请微专业"),
    MS_ENROLLMENT_FAILED("MS_ENROLLMENT_FAILED", "微专业未通过"),
    MS_CERTIFICATE_ISSUED("MS_CERTIFICATE_ISSUED", "微专业证书已颁发"),
    MS_COMPLETED("MS_COMPLETED", "微专业已结业"),
    MS_TEAM_REMOVED("MS_TEAM_REMOVED", "已被移出微专业团队"),
    MS_TEAM_LEFT("MS_TEAM_LEFT", "团队成员已退出"),
    MS_CANCELLED("MS_CANCELLED", "微专业已取消"),
    MS_LEAD_TRANSFERRED("MS_LEAD_TRANSFERRED", "负责人已变更"),
    MS_ARCHIVED("MS_ARCHIVED", "微专业已归档");

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

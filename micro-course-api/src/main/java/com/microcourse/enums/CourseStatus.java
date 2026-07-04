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

    public static CourseStatus fromCode(Integer code) {
        if (code == null) return null;
        for (CourseStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /**
     * ★ 业务逻辑审计 P2-1 修复：课程状态机集中白名单。
     * <p>对齐 docs/状态机设计.md §2.2 状态转换图：</p>
     * <ul>
     *   <li>DRAFT → PENDING_REVIEW（教师提交审核）</li>
     *   <li>PENDING_REVIEW → APPROVED / REJECTED（管理员）</li>
     *   <li>REJECTED → DRAFT / PENDING_REVIEW（教师修改后重提或保存）</li>
     *   <li>APPROVED → PUBLISHED（教师发布）</li>
     *   <li>PUBLISHED → CLOSED / ARCHIVED</li>
     *   <li>CLOSED → PUBLISHED / ARCHIVED（重新上架或归档）</li>
     *   <li>ARCHIVED = 终态</li>
     * </ul>
     * <p>注：原实现 {@code CourseServiceImpl.isValidTransition()} 是私有散落校验，
     * 现在统一到枚举，业务方法直接调用此方法。</p>
     */
    public boolean canTransitionTo(CourseStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case DRAFT:
                return target == PENDING_REVIEW || target == CLOSED;  // CLOSED: 删除操作
            case PENDING_REVIEW:
                return target == APPROVED || target == REJECTED || target == DRAFT;
            case APPROVED:
                return target == PUBLISHED || target == CLOSED;
            case PUBLISHED:
                return target == CLOSED || target == ARCHIVED;
            case CLOSED:
                return target == PUBLISHED || target == ARCHIVED;
            case REJECTED:
                return target == DRAFT || target == PENDING_REVIEW || target == ARCHIVED || target == CLOSED;  // CLOSED: 删除操作
            case ARCHIVED:  // 终态
            default:
                return false;
        }
    }

    /**
     * ★ 客户体验修复 v1.7.0: 课程是否可被学生选课/购买。
     * <p>P0 生产 bug: 5 门核心 seed 课程卡在 APPROVED (status=2),但 published_at 已设置、
     * 学生已实际在学,UI 课程广场也展示为可购买。旧 API 检查 == PUBLISHED (4) 直接拒绝,
     * 导致 ¥9.99 付费课程"立即购买"按钮点了弹"课程未发布"。</p>
     * <p>修复: 定义"可被选课"=APPROVED (管理员已通过) || PUBLISHED (教师已发布)。
     * 业务上,管理员通过后即使教师没点"正式发布",课程也已经具备所有选课条件。</p>
     */
    public boolean isSelectable() {
        return this == APPROVED || this == PUBLISHED;
    }
}
package com.microcourse.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 选课状态枚举。
 *
 * 历史兼容：存量数据使用 {@code ENROLLED} 值，新代码统一走 {@code APPROVED}。
 * {@link #LEGACY_ENROLLED_VALUE} 用于兼容查询。{@link #fromString(String)} 将历史 {@code ENROLLED} 
 * 字符串映射为 {@code APPROVED} 枚举。
 *
 * 注：V148 migration 已完成存量 ENROLLED → APPROVED 迁移。
 * 新建选课记录写入 APPROVED（{@code EnrollmentStatus.APPROVED.getValue()}）。
 *
 * @since Phase 2
 * @see docs/状态机设计.md §8.2
 */
public enum EnrollmentStatus {

    /** 待审核（状态机初始状态）。 */
    PENDING("PENDING"),
    /** 已通过（在读）。历史脏值 "ENROLLED" 经 {@link #fromString(String)} 归一到此状态。 */
    APPROVED("APPROVED"),
    /** 候补中。 */
    WAITLIST("WAITLIST"),
    /** 已取消（终态）。 */
    CANCELLED("CANCELLED"),
    /** 已拒绝（终态）。 */
    REJECTED("REJECTED"),
    /** 已完成（终态）。 */
    COMPLETED("COMPLETED"),
    /** 已退课（终态）。 */
    DROPPED("DROPPED");

    /**
     * 历史遗留值 —— 已废弃。
     * 新代码统一使用 {@link #APPROVED}，不再写入此值。
     * 保留仅供 {@link #fromString(String)} 后向兼容存量数据。
     * @deprecated 不再写入，仅用于解析存量数据
     */
    @Deprecated
    public static final String LEGACY_ENROLLED_VALUE = "ENROLLED";

    @EnumValue   // MyBatis-Plus 持久化用（实体字段切换为本枚举类型时生效）
    @JsonValue   // Jackson 序列化用（实体字段切换为本枚举类型时生效）
    private final String value;

    EnrollmentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 容错解析：把任意字符串归一为契约枚举。
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>历史脏值 "ENROLLED"（忽略大小写）→ {@link #APPROVED}</li>
     *   <li>契约枚举名/值（忽略大小写）→ 对应枚举</li>
     *   <li>其余 → 抛 {@link IllegalArgumentException}</li>
     * </ul>
     */
    public static EnrollmentStatus fromString(String s) {
        if (s == null) {
            return null;
        }
        // 兼容历史脏数据：契约外的 "ENROLLED" 归一为 APPROVED
        if (LEGACY_ENROLLED_VALUE.equalsIgnoreCase(s)) {
            return APPROVED;
        }
        for (EnrollmentStatus status : values()) {
            if (status.value.equalsIgnoreCase(s) || status.name().equalsIgnoreCase(s)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown enrollment status: " + s);
    }

    /**
     * 合法状态流转白名单（对齐 docs/状态机设计.md §3.2 状态转换图）。
     *
     * @param target 目标状态
     * @return 是否允许从当前状态流转到 target；目标为 null 或与当前相同时返回 false
     */
    public boolean canTransitionTo(EnrollmentStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == APPROVED || target == REJECTED || target == WAITLIST || target == CANCELLED;
            case APPROVED:
                return target == COMPLETED || target == CANCELLED || target == DROPPED;
            case WAITLIST:
                return target == APPROVED || target == CANCELLED;
            case REJECTED:   // 终态
            case CANCELLED:  // 终态
            case COMPLETED:  // 终态
            case DROPPED:    // 终态
            default:
                return false;
        }
    }
}

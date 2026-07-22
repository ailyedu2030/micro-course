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
 * @since Phase 2 */
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
    DROPPED("DROPPED"),
    /** 已暂停（用户被禁用后级联暂停，非终态，管理员恢复用户后可恢复）。 */
    SUSPENDED("SUSPENDED"),
    /** 重新选课中（从 CANCELLED 恢复重新选课时的中间状态，标记旧记录）。 */
    REENROLLING("REENROLLING");

    /**
     * 历史遗留值 —— 已废弃。
     * 新代码统一使用 {@link #APPROVED}，不再写入此值。
     * 保留仅供 {@link #fromString(String)} 后向兼容存量数据。
     * @deprecated 不再写入，仅用于解析存量数据
     */
    @Deprecated
    public static final String LEGACY_ENROLLED_VALUE = "ENROLLED";

    /**
     * 历史兼容：枚举迁移 V148 已完成, 但部分 SQL 查询 / 外部数据源仍可能传入 "ENROLLED"。
     * 返回当前"在读"状态值集合(当前 APPROVED + 历史 ENROLLED), 供 query 兼容。
     *
     * <p>注意: 写入应使用 {@link #APPROVED}, 本方法只读用。
     * 当存量数据完全迁移完毕, 移除 {@link #LEGACY_ENROLLED_VALUE} 即可, 业务侧无感。</p>
     */
    private static final java.util.Set<String> LEGACY_ACTIVE_VALUES =
            java.util.Set.of(LEGACY_ENROLLED_VALUE, APPROVED.getValue());

    public static java.util.Set<String> legacyAndActiveEnrolledValues() {
        return LEGACY_ACTIVE_VALUES;
    }

    /**
     * 历史兼容 + 额外状态值。用于更宽的 in 查询 (如"在读或已完成"的查询条件)。
     *
     * @param extras 额外状态值, 传 {@code null} 元素会被跳过
     * @return 包含历史 ENROLLED、当前 APPROVED, 以及 extras 中所有非 null 值
     */
    public static java.util.Set<String> legacyActiveWith(String... extras) {
        java.util.Set<String> set = new java.util.HashSet<>(LEGACY_ACTIVE_VALUES);
        if (extras != null) {
            for (String e : extras) {
                if (e != null) set.add(e);
            }
        }
        return set;
    }

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
                return target == COMPLETED || target == CANCELLED || target == DROPPED || target == SUSPENDED;
            case WAITLIST:
                return target == APPROVED || target == CANCELLED || target == SUSPENDED;
            case SUSPENDED:
                return target == APPROVED || target == CANCELLED;
            case CANCELLED:
                // P1C-008: 允许退课后再选课（标记旧记录为 REENROLLING）
                return target == REENROLLING;
            case REJECTED:   // 终态
            case COMPLETED:  // 终态
            case DROPPED:    // 终态
            case REENROLLING: // 终态（旧记录的标记状态）
            default:
                return false;
        }
    }
}

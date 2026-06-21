package com.microcourse.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 选课状态枚举（对齐 docs/状态机设计.md §3 + docs/数据字典.md v0.5 §enrollments.enrollment_status）
 *
 * <p>契约枚举值仅 7 个：PENDING / APPROVED / WAITLIST / CANCELLED / REJECTED / COMPLETED / DROPPED。
 * 数据字典中 <b>根本不含</b> "ENROLLED" —— 历史代码写入的 "ENROLLED" 是契约外魔法字符串。</p>
 *
 * <p>P0-2 兼容性策略（UX 零退化）：</p>
 * <ul>
 *   <li>DB / API 中存量的历史值 "ENROLLED" 通过 {@link #fromString(String)} 映射为 {@link #APPROVED}（语义等价：已通过/在读）。</li>
 *   <li>为保证前端无感升级与现有 API 响应字符串值不变，新建选课记录的 enrollment_status <b>仍写入</b>
 *       {@link #LEGACY_ENROLLED_VALUE}（= "ENROLLED"），待后续数据迁移完成后再切换为 {@link #APPROVED} 的契约值。
 *       这是"灰度可控"约束的体现：代码内部用枚举做类型安全与状态机校验，对外字符串保持向后兼容。</li>
 *   <li>{@link EnumValue} / {@link JsonValue} 预留，便于后续将实体字段切换为本枚举类型时自动持久化/序列化。</li>
 * </ul>
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
     * 历史遗留的"在读/已选"对外字符串值。
     *
     * <p>该值 <b>不是</b> 数据字典契约内的枚举名，而是旧代码（EnrollmentServiceImpl）契约外写入的魔法字符串，
     * 语义等价于 {@link #APPROVED}。集中定义于此，消除散落的魔法字符串，并标记为后续可灰度替换的迁移点：
     * 当 DB 数据从 "ENROLLED" 全量迁移到 "APPROVED" 后，可将所有引用替换为 {@code APPROVED.getValue()} 并删除本常量。</p>
     */
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

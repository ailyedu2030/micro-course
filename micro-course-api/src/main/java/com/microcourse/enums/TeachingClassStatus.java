package com.microcourse.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 教学班状态枚举（对齐 docs/状态机设计.md §4 + docs/数据字典.md v0.5 §2.9 teaching_classes.status）。
 *
 * <p>契约状态值（与 V32__teaching_classes.sql 建表注释一致）：</p>
 * <ul>
 *   <li>0 = CANCELLED（已停开）—— 教学班取消，不再使用（终态）</li>
 *   <li>1 = ACTIVE（开课中）—— 教学班正在进行（初始状态）</li>
 *   <li>2 = COMPLETED（已结课）—— 本学期教学已完成（终态）</li>
 * </ul>
 *
 * <p>合法流转（状态机设计 §4.2）：</p>
 * <ul>
 *   <li>(创建) → ACTIVE：学期开始 / 教学班创建（{@code status = 1}）</li>
 *   <li>ACTIVE → COMPLETED：结课（{@code status = 2}）</li>
 *   <li>ACTIVE → CANCELLED：停开（{@code status = 0}）</li>
 *   <li>COMPLETED → （终态）</li>
 *   <li>CANCELLED → （终态）</li>
 * </ul>
 *
 * <p><b>注意</b>：教学班初始创建应为 ACTIVE(1)，不是 CANCELLED(0)。
 * 历史代码 {@code TeachingClassServiceImpl.create()} 误写 {@code status = 0}，
 * 导致新建教学班一创建就处于"已停开"状态，与 DB DEFAULT 1 及状态机设计相悖（Round 6 修复）。</p>
 *
 * <p>{@link EnumValue} / {@link JsonValue} 预留：便于后续将实体 {@code status} 字段
 * 切换为本枚举类型时自动持久化（MyBatis-Plus）/序列化（Jackson）。</p>
 */
public enum TeachingClassStatus {

    /** 已停开（终态）。 */
    CANCELLED(0, "已停开"),
    /** 开课中（初始状态）。 */
    ACTIVE(1, "开课中"),
    /** 已结课（终态）。 */
    COMPLETED(2, "已结课");

    @EnumValue   // MyBatis-Plus 持久化用（实体字段切换为本枚举类型时生效）
    @JsonValue   // Jackson 序列化用（实体字段切换为本枚举类型时生效）
    private final Integer code;

    private final String label;

    TeachingClassStatus(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public Integer getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 合法状态流转白名单（对齐 docs/状态机设计.md §4.2 状态转换图）。
     *
     * @param target 目标状态
     * @return 是否允许从当前状态流转到 target；目标为 null 或与当前相同时返回 false
     */
    public boolean canTransitionTo(TeachingClassStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case ACTIVE:
                return target == COMPLETED || target == CANCELLED;
            case COMPLETED:  // 终态
            case CANCELLED:  // 终态
            default:
                return false;
        }
    }

    /**
     * 由状态码解析枚举。
     *
     * @param code 状态码（0/1/2）
     * @return 对应枚举；code 为 null 时返回 null
     * @throws IllegalArgumentException 未知状态码
     */
    public static TeachingClassStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TeachingClassStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown teaching class status code: " + code);
    }
}

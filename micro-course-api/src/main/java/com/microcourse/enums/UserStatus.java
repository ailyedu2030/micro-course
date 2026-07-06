package com.microcourse.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 用户状态枚举（对齐 docs/数据字典.md v0.5 §1.1 users.status + docs/状态机设计.md §1）。
 *
 * <p>范式与 {@link EnrollmentStatus} 一致：{@link EnumValue} 持久化 + {@link JsonValue} 序列化
 * + {@link #canTransitionTo} 状态流转白名单 + {@link #fromCode} 容错解析。</p>
 *
 * <p>状态值（与 users.status 整型列一一对应）：</p>
 * <ul>
 *   <li>0 = INACTIVE（未激活）</li>
 *   <li>1 = ACTIVE（正常）</li>
 *   <li>2 = DISABLED（已禁用）</li>
 *   <li>3 = DELETED（已删除 / 软删除，180 天保留）</li>
 * </ul>
 *
 * <p>合法流转（对齐状态机设计 §1.2）：</p>
 * <ul>
 *   <li>INACTIVE → ACTIVE / DELETED</li>
 *   <li>ACTIVE → DISABLED / DELETED</li>
 *   <li>DISABLED → ACTIVE / DELETED</li>
 *   <li>DELETED → ACTIVE（恢复，业务层校验删除距今 ≤ 180 天）</li>
 * </ul>
 *
 * <p>注意：{@link EnumValue}/{@link JsonValue} 预留，便于后续将 {@code User.status} 字段
 * 由 {@code Integer} 切换为本枚举类型时自动持久化/序列化（与 EnrollmentStatus 同款灰度策略）。</p>
 */
public enum UserStatus {

    /** 未激活。 */
    INACTIVE(0, "INACTIVE"),
    /** 正常（启用）。 */
    ACTIVE(1, "ACTIVE"),
    /** 已禁用。 */
    DISABLED(2, "DISABLED"),
    /** 已删除（软删除，180 天保留窗口内可恢复为 ACTIVE）。 */
    DELETED(3, "DELETED");

    @EnumValue   // MyBatis-Plus 持久化用（实体字段切换为本枚举类型时生效）
    @JsonValue   // Jackson 序列化用（实体字段切换为本枚举类型时生效）
    private final Integer code;

    private final String label;

    UserStatus(Integer code, String label) {
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
     * 合法状态流转白名单（对齐 docs/状态机设计.md §1.2 状态转换图）。
     *
     * @param target 目标状态
     * @return 是否允许从当前状态流转到 target；目标为 null 或与当前相同时返回 false
     */
    public boolean canTransitionTo(UserStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case INACTIVE:
                // 【P1-C 修复】移除 INACTIVE→DELETED 超额转换
                // 设计文档 §1.3 T4 明确禁止 (仅 ACTIVE/DISABLED → DELETED)
                // INACTIVE 用户需先激活 (ACTIVE) 再走正常删除路径
                // 物理清理由 UserRetentionCleanupJob @Scheduled 处理
                return target == ACTIVE;
            case ACTIVE:
                return target == DISABLED || target == DELETED;
            case DISABLED:
                return target == ACTIVE || target == DELETED;
            case DELETED:
                return target == ACTIVE; // S-05: 恢复目标为 ACTIVE(1)；180 天窗口由业务层校验
            default:
                return false;
        }
    }

    /**
     * 容错解析：把状态码（0/1/2/3）归一为枚举。
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>0/1/2/3 → 对应枚举</li>
     *   <li>其余 → 抛 {@link IllegalArgumentException}</li>
     * </ul>
     */
    public static UserStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown user status code: " + code);
    }
}

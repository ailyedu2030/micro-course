package com.microcourse.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 订单状态枚举（对齐 docs/数据字典.md v0.5 · orders.status + docs/状态机设计.md 订单状态机）。
 *
 * <p>持久化范式：与 {@link EnrollmentStatus} 一致 —— 使用 <b>String</b> 值持久化（{@link EnumValue} +
 * {@link JsonValue}），而非 {@link com.microcourse.enums.UserStatus} 的 Integer 范式。
 * orders.status 列为 VARCHAR(20)（见 V50__orders_and_payments.sql），存量值即为下列大写字符串，
 * 因此本枚举可零迁移地与现有数据兼容。</p>
 *
 * <p>状态值（与现有字符串存储完全兼容）：</p>
 * <ul>
 *   <li>{@link #PENDING}  待支付（订单初始状态）</li>
 *   <li>{@link #PAID}     已支付</li>
 *   <li>{@link #CANCELLED} 已取消（终态）</li>
 *   <li>{@link #REFUNDED} 已退款（终态）</li>
 * </ul>
 *
 * <p>合法流转白名单（对齐状态机设计）：</p>
 * <ul>
 *   <li>PENDING → PAID（支付成功 / 支付回调）</li>
 *   <li>PENDING → CANCELLED（用户取消 / 超时）</li>
 *   <li>PAID → REFUNDED（退款申请通过）</li>
 *   <li>终态：CANCELLED / REFUNDED —— 不允许任何后继流转</li>
 * </ul>
 *
 * <p>Round 6-3 补完：本枚举与 V65 配套落地，为订单状态转换提供类型安全的白名单校验，
 * 与 OrderServiceImpl 的 CAS 乐观锁形成「业务语义 + 并发竞态」双重防御。</p>
 */
public enum OrderStatus {

    /** 待支付（订单初始状态）。 */
    PENDING("PENDING", "待支付"),
    /** 已支付。 */
    PAID("PAID", "已支付"),
    /** 已取消（终态）。 */
    CANCELLED("CANCELLED", "已取消"),
    /** 已退款（终态）。 */
    REFUNDED("REFUNDED", "已退款");

    @EnumValue   // MyBatis-Plus 持久化用（实体字段切换为本枚举类型时生效）
    @JsonValue   // Jackson 序列化用（实体字段切换为本枚举类型时生效）
    private final String value;

    private final String label;

    OrderStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 合法状态流转白名单（对齐 docs/状态机设计.md 订单状态机）。
     *
     * @param target 目标状态
     * @return 是否允许从当前状态流转到 target；target 为 null 或与当前相同时返回 false
     */
    public boolean canTransitionTo(OrderStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == PAID || target == CANCELLED;
            case PAID:
                return target == REFUNDED;
            case CANCELLED: // 终态
            case REFUNDED:  // 终态
            default:
                return false;
        }
    }

    /**
     * 容错解析：把字符串归一为契约枚举（大小写不敏感）。
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>契约枚举值 / 枚举名（忽略大小写）→ 对应枚举</li>
     *   <li>其余 → 抛 {@link IllegalArgumentException}</li>
     * </ul>
     */
    public static OrderStatus fromValue(String s) {
        if (s == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.value.equalsIgnoreCase(s) || status.name().equalsIgnoreCase(s)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + s);
    }
}

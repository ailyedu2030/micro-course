package com.microcourse.util;

/**
 * 状态字段类型转换工具（P2-1 修复）。
 *
 * <p>状态字段类型约定：</p>
 * <ul>
 *   <li><b>Integer</b>：用于有数值序列的状态（如 0=INACTIVE, 1=ACTIVE）
 *     <ul>
 *       <li>users.status（见 {@code com.microcourse.enums.UserStatus}）</li>
 *       <li>courses.status（见 {@code com.microcourse.enums.CourseStatus}）</li>
 *       <li>videos.status</li>
 *     </ul>
 *   </li>
 *   <li><b>String</b>：用于语义化状态（便于阅读和扩展）
 *     <ul>
 *       <li>enrollments.enrollment_status（见 {@code com.microcourse.enums.EnrollmentStatus}）</li>
 *       <li>orders.status（见 {@code com.microcourse.enums.OrderStatus}）</li>
 *       <li>teaching_classes.status（见 {@code com.microcourse.enums.TeachingClassStatus}）</li>
 *       <li>notifications.type（见 {@code com.microcourse.enums.NotificationType}）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>迁移指南：</p>
 * <ol>
 *   <li>新增状态字段优先使用 Integer（性能更好、索引更高效）。</li>
 *   <li>已有 String 字段不强制迁移（避免破坏 API 契约）。</li>
 *   <li>如需将 Integer 字段改为 String，需经过 Phase H 数据迁移。</li>
 * </ol>
 *
 * <p>本工具类仅做 <b>容错解析</b>，不改变任何字段的持久化类型与序列化输出，
 * 因此对现有 API 契约与用户体验零影响（P2-1 UX 零退化约束）。</p>
 *
 * <p>状态字段类型决策的完整文档见 {@code docs/开发规范.md} 中
 * 「状态字段类型约定」一节。</p>
 */
public final class StatusConverter {

    private StatusConverter() {
    }

    /**
     * 安全解析 Integer 状态码。
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>{@link Integer} → 原值</li>
     *   <li>其它 {@link Number} → {@link Number#intValue()}</li>
     *   <li>可解析为整数的 {@link String} → 对应 Integer</li>
     *   <li>不可解析 → null</li>
     * </ul>
     *
     * @param value 任意来源的状态值
     * @return 解析后的 Integer 状态码，无法解析时返回 null
     */
    public static Integer parseIntStatus(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 安全解析 String 状态值。
     *
     * <ul>
     *   <li>null → null</li>
     *   <li>其它 → {@code value.toString()}</li>
     * </ul>
     *
     * @param value 任意来源的状态值
     * @return 解析后的 String 状态值，输入为 null 时返回 null
     */
    public static String parseStringStatus(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}

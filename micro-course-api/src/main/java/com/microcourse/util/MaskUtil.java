package com.microcourse.util;

/**
 * 敏感字段脱敏工具（Round 11-1 数据隔离）。
 *
 * <p>提供手机号 / 邮箱的统一脱敏规则，供 Service 层按角色对外暴露敏感字段时复用。
 * 规则与 {@code UserServiceImpl} 列表端既有脱敏逻辑完全一致，保证全站脱敏格式统一：</p>
 * <ul>
 *   <li>{@link #maskPhone}：保留前 3 位与后 4 位，中间以 {@code ****} 掩盖（如 {@code 138****1234}）；</li>
 *   <li>{@link #maskEmail}：保留首字符与 {@code @} 后域名，前缀其余以 {@code ***} 掩盖（如 {@code a***@example.com}）。</li>
 * </ul>
 *
 * <p>边界安全：入参为 {@code null} 或不满足脱敏前提（如手机号过短 / 邮箱无 {@code @}）时原样返回，
 * 不抛异常，避免影响合法字段展示（UX 零退化）。</p>
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * 手机号脱敏：{@code 13812341234 -> 138****1234}。
     * 长度小于 7 或为 {@code null} 时原样返回。
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏：{@code alice@example.com -> a***@example.com}。
     * 为 {@code null} 或不含 {@code @} 时原样返回。
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int at = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(at);
    }
}

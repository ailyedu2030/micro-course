package com.microcourse.util;

/**
 * P1 安全修复: 日志注入防护工具类。
 *
 * <p>用户可控输入直接拼接到日志格式字符串中可能导致日志注入（CRLF 注入、日志伪造）。
 * 本工具类将用户输入中的控制字符替换为安全占位符后再写入日志。
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 清理用户输入用于日志输出。
     * 替换 CR（\r）、LF（\n）为下划线，防止日志注入（CRLF 注入/日志行伪造）。
     *
     * @param input 原始输入，可为 null
     * @return 清理后的字符串；input 为 null 时返回空字符串
     */
    public static String sanitizeForLog(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('\r', '_').replace('\n', '_');
    }

    /**
     * 清理用户输入用于日志输出，保留更多可读性。
     * 替换 CR（\r）、LF（\n）为空格，并截断过长的输入。
     *
     * @param input  原始输入，可为 null
     * @param maxLen 最大长度，超出部分用 "..." 截断
     * @return 清理后的字符串；input 为 null 时返回空字符串
     */
    public static String sanitizeForLog(String input, int maxLen) {
        if (input == null) {
            return "";
        }
        String sanitized = input.replace('\r', ' ').replace('\n', ' ');
        if (sanitized.length() > maxLen) {
            return sanitized.substring(0, maxLen) + "...";
        }
        return sanitized;
    }
}

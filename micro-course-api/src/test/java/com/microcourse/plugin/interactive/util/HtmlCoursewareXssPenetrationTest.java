package com.microcourse.plugin.interactive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XSS 渗透测试套件 — 覆盖 10 个 OWASP Top 10 XSS 攻击 payload。
 * <p>
 * 测试目标方法：
 * <ul>
 *   <li>{@link HtmlSanitizer#sanitize(String)} — 严格模式消毒</li>
 *   <li>{@link HtmlSanitizer#containsDisallowedContent(String)} — 快速拒绝扫描</li>
 *   <li>{@link HtmlSanitizer#sanitizeForCourseware(String)} — 课件宽松消毒（安全由 iframe sandbox 兜底）</li>
 * </ul>
 * </p>
 *
 * <h3>Payload 覆盖</h3>
 * <ol>
 *   <li>&lt;script&gt;alert(1)&lt;/script&gt;</li>
 *   <li>&lt;img src=x onerror=alert(1)&gt;</li>
 *   <li>&lt;a href="javascript:alert(1)"&gt;click&lt;/a&gt;</li>
 *   <li>&lt;svg onload=alert(1)&gt;</li>
 *   <li>&lt;iframe src=javascript:alert(1)&gt;</li>
 *   <li>&lt;body onload=alert(1)&gt;</li>
 *   <li>&lt;style&gt;body{background:url(javascript:alert(1))}&lt;/style&gt;</li>
 *   <li>&lt;meta http-equiv=refresh content=0;url=javascript:alert(1)&gt;</li>
 *   <li>&lt;form action=javascript:alert(1)&gt;</li>
 *   <li>&lt;base href=javascript:alert(1)&gt;</li>
 * </ol>
 */
class HtmlCoursewareXssPenetrationTest {

    /**
     * 10 个 OWASP XSS 攻击 payload。
     */
    static Stream<XssTestCase> xssPayloads() {
        return Stream.of(
                new XssTestCase(1, "<script>alert(1)</script>",
                        "脚本注入: <script> 标签"),
                new XssTestCase(2, "<img src=x onerror=alert(1)>",
                        "事件处理器: <img onerror>"),
                new XssTestCase(3, "<a href=\"javascript:alert(1)\">click</a>",
                        "伪协议: javascript: URL"),
                new XssTestCase(4, "<svg onload=alert(1)>",
                        "SVG 向量: <svg onload>"),
                new XssTestCase(5, "<iframe src=javascript:alert(1)>",
                        "内联框架: <iframe>"),
                new XssTestCase(6, "<body onload=alert(1)>",
                        "Body 事件: <body onload>"),
                new XssTestCase(7, "<style>body{background:url(javascript:alert(1))}</style>",
                        "CSS 注入: <style> + javascript: URL"),
                new XssTestCase(8, "<meta http-equiv=refresh content=0;url=javascript:alert(1)>",
                        "Meta 重定向: <meta http-equiv=refresh>"),
                new XssTestCase(9, "<form action=javascript:alert(1)>",
                        "表单劫持: <form action=javascript:>"),
                new XssTestCase(10, "<base href=javascript:alert(1)>",
                        "Base 劫持: <base href=javascript:>")
        );
    }

    // ========================================================================
    // 1. containsDisallowedContent() — 快速拒绝扫描
    // ========================================================================

    @ParameterizedTest
    @MethodSource("xssPayloads")
    @DisplayName("containsDisallowedContent() 检测所有 10 个 XSS payload")
    void containsDisallowedContent_detectsAllXssPayloads(XssTestCase testCase) {
        assertTrue(
                HtmlSanitizer.containsDisallowedContent(testCase.payload),
                () -> "应检测到危险内容 [payload " + testCase.id + "]: " + testCase.description
        );
    }

    // ========================================================================
    // 2. sanitize() — 严格模式消毒
    // ========================================================================

    @ParameterizedTest
    @MethodSource("xssPayloads")
    @DisplayName("sanitize() 移除所有 10 个 XSS payload 的危险标签")
    void sanitize_removesAllXssPayloads(XssTestCase testCase) {
        String safe = HtmlSanitizer.sanitize(testCase.payload);

        // 所有危险标签/属性应被移除
        assertAll(
                () -> assertFalse(
                        containsAny(safe, "<script", "onerror=", "onload=", "onclick=",
                                "onmouseover", "onfocus", "onblur"),
                        () -> "sanitize 后不应含事件处理器 [payload " + testCase.id + "]: " + safe),
                () -> assertFalse(
                        containsAny(safe, "javascript:", "vbscript:"),
                        () -> "sanitize 后不应含 javascript: URL [payload " + testCase.id + "]: " + safe),
                () -> assertFalse(
                        containsAny(safe, "<iframe", "<embed", "<object", "<form", "<svg ", "<math "),
                        () -> "sanitize 后不应含禁止标签 [payload " + testCase.id + "]: " + safe),
                () -> assertFalse(
                        containsAny(safe, "<style", "<meta", "<base"),
                        () -> "sanitize 后不应含 style/meta/base 标签 [payload " + testCase.id + "]: " + safe)
        );
    }

    @Test
    @DisplayName("sanitize() 对多重嵌套 XSS 进行深层清理")
    void sanitize_nestedXss() {
        String nestedPayload = "<div><script>alert(1)</script><img src=x onerror=alert(2)></div>";
        String safe = HtmlSanitizer.sanitize(nestedPayload);
        assertFalse(safe.contains("<script"), "嵌套 <script> 应被移除");
        assertFalse(safe.contains("onerror="), "嵌套 onerror 应被移除");
    }

    @Test
    @DisplayName("sanitize() 对编码尝试进行清理")
    void sanitize_encodedXss() {
        // 注意：Jsoup 默认不解码 HTML 实体后再清理，但常见的 <script> 裸标签会被检测
        String encodedPayload = "&lt;script&gt;alert(1)&lt;/script&gt;";
        String safe = HtmlSanitizer.sanitize(encodedPayload);
        // 编码内容 Jsoup 可能转义为文本，不包含可执行脚本
        assertFalse(safe.contains("<script>"), "编码后的 <script> 不应以可执行形式出现");
    }

    // ========================================================================
    // 3. sanitizeForCourseware() — 课件宽松消毒（安全由 iframe sandbox 兜底）
    // ========================================================================

    @ParameterizedTest
    @MethodSource("xssPayloads")
    @DisplayName("sanitizeForCourseware() 对所有 10 个 XSS payload 不抛异常")
    void sanitizeForCourseware_noException(XssTestCase testCase) {
        // 课件模式：安全由前端 iframe sandbox="allow-scripts"（无 allow-same-origin）兜底
        // COURSEWARE_SAFELIST 包含 script/style/iframe/form/input/svg 等标签，
        // 但 body/meta/base 标签不在 safelist 中 → Jsoup 会剥离这些标签
        assertDoesNotThrow(() -> HtmlSanitizer.sanitizeForCourseware(testCase.payload),
                () -> "课件模式不应抛异常 [payload " + testCase.id + "]");
    }

    /**
     * 外层标签在 COURSEWARE_SAFELIST 中的 payload（预期被保留 → 非空）。
     * body/meta/base 不在 safelist 中，不在此清单中。
     */
    static Stream<XssTestCase> preservablePayloads() {
        return Stream.of(
                new XssTestCase(1, "<script>alert(1)</script>", "<script>"),
                new XssTestCase(2, "<img src=x onerror=alert(1)>", "<img>"),
                new XssTestCase(3, "<a href=\"javascript:alert(1)\">click</a>", "<a>"),
                new XssTestCase(4, "<svg onload=alert(1)>", "<svg>"),
                new XssTestCase(5, "<iframe src=javascript:alert(1)>", "<iframe>"),
                new XssTestCase(7, "<style>body{background:url(javascript:alert(1))}</style>", "<style>"),
                new XssTestCase(9, "<form action=javascript:alert(1)>", "<form>")
        );
    }

    @ParameterizedTest
    @MethodSource("preservablePayloads")
    @DisplayName("sanitizeForCourseware() 保留允许标签中的 XSS 内容（sandbox 兜底）")
    void sanitizeForCourseware_preservesAllowedTags(XssTestCase testCase) {
        // 课件模式：保留 script/style/form/iframe/svg/a/img 等教学标签
        // 安全由前端 iframe sandbox="allow-scripts"（无 allow-same-origin）兜底
        String safe = HtmlSanitizer.sanitizeForCourseware(testCase.payload);
        assertFalse(safe.isEmpty(),
                () -> "课件模式应保留内容 [payload " + testCase.id + "]: " + testCase.description);
    }

    @Test
    @DisplayName("sanitizeForCourseware() 剥离 body/meta/base 标签")
    void sanitizeForCourseware_restrictedTags() {
        // body 不在 safelist 中
        assertTrue(HtmlSanitizer.sanitizeForCourseware("<body onload=alert(1)>").isEmpty(),
                "body 标签不在 COURSEWARE_SAFELIST 中，应被剥离");
        // meta 不在 safelist 中
        assertTrue(HtmlSanitizer.sanitizeForCourseware("<meta http-equiv=refresh content=0;url=javascript:alert(1)>").isEmpty(),
                "meta 标签不在 COURSEWARE_SAFELIST 中，应被剥离");
        // base 不在 safelist 中
        assertTrue(HtmlSanitizer.sanitizeForCourseware("<base href=javascript:alert(1)>").isEmpty(),
                "base 标签不在 COURSEWARE_SAFELIST 中，应被剥离");
    }

    @Test
    @DisplayName("sanitizeForCourseware() 保留混合内容（安全内容 + XSS）")
    void sanitizeForCourseware_mixedContent() {
        String mixed = "<p>正常段落</p><script>console.log('test')</script><p>更多内容</p>";
        String safe = HtmlSanitizer.sanitizeForCourseware(mixed);
        assertTrue(safe.contains("正常段落"), "正常文本应保留");
        assertTrue(safe.contains("script"), "script 标签应保留（sandbox 兜底）");
    }

    // ========================================================================
    // 4. 边缘情况
    // ========================================================================

    @Test
    @DisplayName("空的 null/空白输入不抛异常")
    void nullAndEmptyInput() {
        assertDoesNotThrow(() -> HtmlSanitizer.containsDisallowedContent(null));
        assertDoesNotThrow(() -> HtmlSanitizer.containsDisallowedContent(""));
        assertDoesNotThrow(() -> HtmlSanitizer.sanitize(null));
        assertDoesNotThrow(() -> HtmlSanitizer.sanitize(""));
        assertDoesNotThrow(() -> HtmlSanitizer.sanitizeForCourseware(null));
        assertDoesNotThrow(() -> HtmlSanitizer.sanitizeForCourseware(""));
    }

    @Test
    @DisplayName("干净内容在严格模式下保持不变")
    void cleanContentPreserved() {
        String clean = "<p>这是正常的教学课件内容。<b>粗体</b>和<em>斜体</em>都支持。</p>";
        String safe = HtmlSanitizer.sanitize(clean);
        assertEquals(clean, safe, "干净内容应保持不变");
    }

    @Test
    @DisplayName("containsDisallowedContent 对干净内容返回 false")
    void containsDisallowedContent_cleanContent() {
        assertFalse(HtmlSanitizer.containsDisallowedContent("<p>Hello world</p>"));
        assertFalse(HtmlSanitizer.containsDisallowedContent("<div>纯文本内容</div>"));
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private static boolean containsAny(String str, String... substrings) {
        if (str == null) return false;
        String lower = str.toLowerCase();
        for (String sub : substrings) {
            if (lower.contains(sub)) return true;
        }
        return false;
    }

    /**
     * XSS 测试用例记录
     */
    static class XssTestCase {
        final int id;
        final String payload;
        final String description;

        XssTestCase(int id, String payload, String description) {
            this.id = id;
            this.payload = payload;
            this.description = description;
        }

        @Override
        public String toString() {
            return "[" + id + "] " + description + ": " + payload;
        }
    }
}

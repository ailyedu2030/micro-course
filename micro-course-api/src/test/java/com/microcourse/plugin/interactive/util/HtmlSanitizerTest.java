package com.microcourse.plugin.interactive.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlSanitizer 单元测试 — 覆盖 10+ XSS payload 及边界情况。
 * <p>
 * 测试目标方法：
 * <ul>
 *   <li>{@link HtmlSanitizer#sanitize(String)} — 严格模式消毒</li>
 *   <li>{@link HtmlSanitizer#containsDisallowedContent(String)} — 快速拒绝扫描</li>
 *   <li>{@link HtmlSanitizer#sanitizeForCourseware(String)} — 课件宽松消毒</li>
 * </ul>
 * </p>
 */
class HtmlSanitizerTest {

    @Nested
    @DisplayName("sanitize() — 严格模式消毒")
    class StrictSanitize {

        @Test
        @DisplayName("1. <script> 标签被移除")
        void scriptTag() {
            String safe = HtmlSanitizer.sanitize("<script>alert(1)</script>");
            assertTrue(safe.isEmpty() || !safe.toLowerCase().contains("<script"),
                    "sanitize 应移除 <script> 标签");
        }

        @Test
        @DisplayName("2. <img onerror> 事件处理器被移除")
        void imgOnerror() {
            String safe = HtmlSanitizer.sanitize("<img src=x onerror=alert(1)>");
            assertFalse(safe.contains("onerror"), "onerror 事件处理器应被移除");
        }

        @Test
        @DisplayName("3. <a href=javascript:> javascript: URL 被剥离")
        void anchorJavascript() {
            String safe = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>");
            // Jsoup 会保留 <a> 标签但移除 href 属性，或完全移除 href
            assertFalse(safe.contains("javascript:"), "javascript: URL 应被移除");
        }

        @Test
        @DisplayName("4. <svg onload> 被移除（svg 不在允许标签列表）")
        void svgOnload() {
            String safe = HtmlSanitizer.sanitize("<svg onload=alert(1)>");
            assertFalse(safe.contains("<svg"), "<svg> 标签应被移除");
        }

        @Test
        @DisplayName("5. <iframe> 被移除（iframe 不在允许标签列表）")
        void iframeTag() {
            String safe = HtmlSanitizer.sanitize("<iframe src=javascript:alert(1)>");
            assertFalse(safe.contains("<iframe"), "<iframe> 标签应被移除");
        }

        @Test
        @DisplayName("6. <body onload> 事件处理器被移除")
        void bodyOnload() {
            String safe = HtmlSanitizer.sanitize("<body onload=alert(1)>");
            assertFalse(safe.contains("onload"), "onload 事件处理器应被移除");
        }

        @Test
        @DisplayName("7. <style> 标签被移除")
        void styleTag() {
            String safe = HtmlSanitizer.sanitize("<style>body{background:url(javascript:alert(1))}</style>");
            assertFalse(safe.contains("<style"), "<style> 标签应被移除");
        }

        @Test
        @DisplayName("8. <meta> 标签被移除")
        void metaTag() {
            String safe = HtmlSanitizer.sanitize("<meta http-equiv=refresh content=0;url=javascript:alert(1)>");
            assertFalse(safe.contains("<meta"), "<meta> 标签应被移除");
        }

        @Test
        @DisplayName("9. <form> 标签被移除")
        void formTag() {
            String safe = HtmlSanitizer.sanitize("<form action=javascript:alert(1)>");
            assertFalse(safe.contains("<form"), "<form> 标签应被移除");
        }

        @Test
        @DisplayName("10. <base> 标签被移除")
        void baseTag() {
            String safe = HtmlSanitizer.sanitize("<base href=javascript:alert(1)>");
            assertFalse(safe.contains("<base"), "<base> 标签应被移除");
        }

        @Test
        @DisplayName("11. <embed> 标签被移除")
        void embedTag() {
            String safe = HtmlSanitizer.sanitize("<embed src=javascript:alert(1)>");
            assertFalse(safe.contains("<embed"), "<embed> 标签应被移除");
        }

        @Test
        @DisplayName("12. 干净的 HTML 保留不动")
        void cleanHtmlPreserved() {
            String raw = "<p>Hello <b>world</b></p>";
            String safe = HtmlSanitizer.sanitize(raw);
            assertEquals(raw, safe, "干净的 HTML 应保持原样");
        }

        @Test
        @DisplayName("13. null 和空字符串返回空")
        void nullAndEmpty() {
            assertEquals("", HtmlSanitizer.sanitize(null));
            assertEquals("", HtmlSanitizer.sanitize(""));
        }
    }

    @Nested
    @DisplayName("containsDisallowedContent() — 快速拒绝扫描")
    class DisallowedContent {

        @Test
        @DisplayName("检测 <script 开头的标签")
        void scriptTag() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<script>alert(1)</script>"));
        }

        @Test
        @DisplayName("检测 onerror= 事件处理器")
        void onerrorHandler() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<img src=x onerror=alert(1)>"));
        }

        @Test
        @DisplayName("检测 onload= 事件处理器")
        void onloadHandler() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<body onload=alert(1)>"));
        }

        @Test
        @DisplayName("检测 javascript: 协议")
        void javascriptProtocol() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<a href=\"javascript:alert(1)\">x</a>"));
        }

        @Test
        @DisplayName("检测 <iframe 标签")
        void iframeTag() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<iframe src=...>"));
        }

        @Test
        @DisplayName("检测 <svg 标签")
        void svgTag() {
            // containsDisallowedContent 检测的是 "<svg " 带空格
            // 但 "<svg onload" 也包含 "<svg"
            assertTrue(HtmlSanitizer.containsDisallowedContent("<svg onload=alert(1)>"));
        }

        @Test
        @DisplayName("检测 <form 标签")
        void formTag() {
            assertTrue(HtmlSanitizer.containsDisallowedContent("<form action=...>"));
        }

        @Test
        @DisplayName("干净内容返回 false")
        void cleanContent() {
            assertFalse(HtmlSanitizer.containsDisallowedContent("<p>Hello world</p>"));
        }

        @Test
        @DisplayName("null 和空字符串返回 false")
        void nullAndEmpty() {
            assertFalse(HtmlSanitizer.containsDisallowedContent(null));
            assertFalse(HtmlSanitizer.containsDisallowedContent(""));
        }
    }

    @Nested
    @DisplayName("sanitizeForCourseware() — 课件宽松消毒")
    class CoursewareSanitize {

        @Test
        @DisplayName("保留教学常用标签（如 iframe）")
        void preservesIframe() {
            String raw = "<iframe src='https://example.com'></iframe>";
            String safe = HtmlSanitizer.sanitizeForCourseware(raw);
            assertTrue(safe.contains("iframe"), "课件模式应保留 <iframe> 标签");
        }

        @Test
        @DisplayName("保留 script 标签（安全由 sandbox 兜底）")
        void preservesScript() {
            String raw = "<script>console.log('hello')</script>";
            String safe = HtmlSanitizer.sanitizeForCourseware(raw);
            assertTrue(safe.contains("script"), "课件模式应保留 <script> 标签");
        }

        @Test
        @DisplayName("清理异常输入不抛异常")
        void nullInput() {
            assertEquals("", HtmlSanitizer.sanitizeForCourseware(null));
            assertEquals("", HtmlSanitizer.sanitizeForCourseware(""));
        }
    }

    @Nested
    @DisplayName("sanitizeForCourseware(rawHtml, trusted) — Q-4 双模式分流 (COURSEWARE_STRICT_SAFELIST)")
    class CoursewareTrustMode {

        @Test
        @DisplayName("1. trusted=true: 保留 script/style/iframe/onclick（宽松模式，sandbox 兜底）")
        void trustedTrueKeepsExecutableContent() {
            String raw = "<script>console.log(1)</script><style>p{color:red}</style>"
                    + "<iframe src=\"https://example.com\"></iframe><button onclick=\"alert(1)\">x</button>";
            String safe = HtmlSanitizer.sanitizeForCourseware(raw, true);
            assertTrue(safe.contains("script"), "trusted=true 应保留 <script>");
            assertTrue(safe.contains("style"), "trusted=true 应保留 <style>");
            assertTrue(safe.contains("iframe"), "trusted=true 应保留 <iframe>");
            assertTrue(safe.contains("onclick"), "trusted=true 应保留 onclick 事件处理器");
        }

        @Test
        @DisplayName("2. trusted=false: script/style/iframe/onclick 全部剥离（严格 Safelist）")
        void trustedFalseStripsExecutableContent() {
            String raw = "<script>alert(1)</script><style>p{color:red}</style>"
                    + "<iframe src=\"https://example.com\"></iframe><button onclick=\"alert(1)\">x</button>";
            String safe = HtmlSanitizer.sanitizeForCourseware(raw, false);
            assertFalse(safe.toLowerCase().contains("<script"), "trusted=false 应剥离 <script>");
            assertFalse(safe.toLowerCase().contains("<style"), "trusted=false 应剥离 <style>");
            assertFalse(safe.toLowerCase().contains("<iframe"), "trusted=false 应剥离 <iframe>");
            assertFalse(safe.contains("onclick"), "trusted=false 应剥离 onclick 事件处理器");
            assertFalse(safe.toLowerCase().contains("<button"), "trusted=false 应剥离 <button>（不在严格白名单）");
        }

        @Test
        @DisplayName("3. 单参 sanitizeForCourseware(html) 默认 trusted=true（与历史一致）")
        void singleArgDefaultsToTrusted() {
            String raw = "<script>console.log('hi')</script>";
            String oneArg = HtmlSanitizer.sanitizeForCourseware(raw);
            String twoArg = HtmlSanitizer.sanitizeForCourseware(raw, true);
            assertEquals(twoArg, oneArg, "单参默认行为应等于 trusted=true");
            assertTrue(oneArg.contains("script"), "历史行为保留 <script>（安全由 sandbox 兜底）");
        }

        @Test
        @DisplayName("4. trusted=false: <script>alert(1)</script> XSS payload 被剥离")
        void trustedFalseStripsScriptPayload() {
            String safe = HtmlSanitizer.sanitizeForCourseware("<script>alert(1)</script>", false);
            assertFalse(safe.toLowerCase().contains("<script"), "<script> 应被剥离");
        }

        @Test
        @DisplayName("5. trusted=false: <img onerror> 事件被剥离，合法 src 保留")
        void trustedFalseStripsImgOnerror() {
            String safe = HtmlSanitizer.sanitizeForCourseware(
                    "<img src=\"https://example.com/x.png\" onerror=\"alert(1)\">", false);
            assertFalse(safe.contains("onerror"), "onerror 事件处理器应被剥离");
            assertTrue(safe.contains("https://example.com/x.png"), "合法 https img src 应保留");
        }

        @Test
        @DisplayName("6. trusted=false: javascript: 协议链接被剥离")
        void trustedFalseStripsJavascriptUrl() {
            String safe = HtmlSanitizer.sanitizeForCourseware(
                    "<a href=\"javascript:alert(1)\">link</a>", false);
            assertFalse(safe.contains("javascript:"), "javascript: href 应被剥离");
        }

        @Test
        @DisplayName("7. trusted=false: <svg onload> 被剥离（svg 不在严格白名单）")
        void trustedFalseStripsSvgOnload() {
            String safe = HtmlSanitizer.sanitizeForCourseware("<svg onload=\"alert(1)\"></svg>", false);
            assertFalse(safe.toLowerCase().contains("<svg"), "<svg> 标签应被剥离");
            assertFalse(safe.contains("onload"), "onload 事件应被剥离");
        }

        @Test
        @DisplayName("8. trusted=false: 保留合法文本格式（p/b/em 等基础标签）")
        void trustedFalseKeepsTextFormatting() {
            String raw = "<p>Hello <b>world</b>, <em>emphasis</em></p>";
            String safe = HtmlSanitizer.sanitizeForCourseware(raw, false);
            assertEquals(raw, safe, "基础文本格式应原样保留");
        }

        @Test
        @DisplayName("9. trusted=false: 保留 http/https 链接")
        void trustedFalseKeepsHttpLinks() {
            String safe = HtmlSanitizer.sanitizeForCourseware(
                    "<a href=\"https://example.com/docs\">文档</a>", false);
            assertTrue(safe.contains("https://example.com/docs"), "https 链接 href 应保留");
        }

        @Test
        @DisplayName("10. trusted=false: 保留 https 图片")
        void trustedFalseKeepsHttpsImage() {
            String safe = HtmlSanitizer.sanitizeForCourseware(
                    "<img src=\"https://example.com/a.png\" alt=\"图\">", false);
            assertTrue(safe.contains("https://example.com/a.png"), "https 图片 src 应保留");
        }

        @Test
        @DisplayName("11. null/空字符串在两种模式均返回空")
        void nullAndEmptyBothModes() {
            assertEquals("", HtmlSanitizer.sanitizeForCourseware(null, true));
            assertEquals("", HtmlSanitizer.sanitizeForCourseware("", true));
            assertEquals("", HtmlSanitizer.sanitizeForCourseware(null, false));
            assertEquals("", HtmlSanitizer.sanitizeForCourseware("", false));
        }
    }
}

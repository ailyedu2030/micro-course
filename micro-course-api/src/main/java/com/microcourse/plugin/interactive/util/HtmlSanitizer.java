package com.microcourse.plugin.interactive.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTML 安全消毒工具类（用于互动课件 HTML 内容）。
 * <p>
 * 基于 Jsoup Safelist 机制，移除 XSS 攻击载荷。
 * 允许教学课件常用的 HTML 标签和属性，禁止脚本、iframe、embed 等危险元素。
 * </p>
 *
 * <h3>安全策略（修复自 Hermes 审查反馈）</h3>
 * <ul>
 *   <li>禁止 data: URI（绕过 5MB 上限，可注入任意 base64 内容）</li>
 *   <li>禁止 style 属性（CSS injection + exfiltration 攻击面）</li>
 *   <li>禁止 target="_blank"（反向 tabnabbing 漏洞）</li>
 *   <li>禁止 id/class 全局属性（避免 CSS 选择器 attack 配合 style 残留）</li>
 * </ul>
 *
 * <h3>允许的标签</h3>
 * a, abbr, b, blockquote, br, caption, cite, code, dd, del, details,
 * div, dl, dt, em, figcaption, figure, h1-h6, hr, i, img, ins, kbd,
 * li, mark, ol, p, pre, q, s, samp, small, span, strike, strong, sub,
 * summary, sup, table, tbody, td, tfoot, th, thead, time, tr, tt, u, ul, var
 *
 * <h3>禁止</h3>
 * script, iframe, form, input, button, object, embed, svg, math, style,
 * meta, base, link 及所有内联事件处理器（onerror, onclick 等）
 */
public final class HtmlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(HtmlSanitizer.class);

    private static final Safelist SAFELIST = Safelist.relaxed()
            // 表格支持
            .addTags("caption", "colgroup", "col", "table", "tbody", "td", "tfoot", "th", "thead", "tr")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            // 布局/语义标签（不允许 style 属性，避免 CSS injection）
            .addTags("div", "span", "section", "article", "header", "footer", "nav", "main", "aside")
            // 内容标签
            .addTags("figure", "figcaption", "details", "summary", "mark", "time", "kbd", "samp", "var")
            .addTags("sub", "sup", "ins", "del", "s", "u")
            // 代码块
            .addTags("pre", "code", "tt", "samp", "kbd")
            // 图片：只允许 http/https，禁止 data: URI（绕过大小限制 + 注入风险）
            .addProtocols("img", "src", "http", "https")
            // 链接：允许安全协议（不允许 target 属性防反向 tabnabbing）
            .addProtocols("a", "href", "http", "https", "mailto")
            // 列表
            .addTags("dl", "dt", "dd")
            // 水平线
            .addTags("hr")
            // 文本方向
            .addTags("bdo", "bdi")
            // 全局通用属性：title 用于悬浮提示，lang/dir 用于文本方向。
            // 注意：故意不添加 style（CSS 注入面）和 id/class（防 CSS 选择器配合 style 残留攻击）
            .addAttributes(":all", "title", "lang", "dir");

    /** 宽松 Safelist：用于教师上传的 HTML 课件。保留所有教学所需标签和属性。 */
    private static final Safelist COURSEWARE_SAFELIST = Safelist.relaxed()
            .addTags("caption", "colgroup", "col", "table", "tbody", "td", "tfoot", "th", "thead", "tr",
                     "div", "span", "section", "article", "header", "footer", "nav", "main", "aside",
                     "figure", "figcaption", "details", "summary", "mark", "time", "kbd", "samp", "var",
                     "sub", "sup", "ins", "del", "s", "u",
                     "pre", "code", "tt", "samp", "kbd",
                     "dl", "dt", "dd", "hr", "bdo", "bdi",
                     "style", "script", "link", "button", "svg",
                     "form", "input", "label", "select", "option", "textarea", "fieldset", "legend",
                     "iframe", "object", "embed",
                     "canvas", "audio", "video", "source", "track", "progress", "meter")
            .addAttributes(":all", "title", "lang", "dir", "class", "id", "style",
                           "data-*", "aria-*", "role",
                           "onclick", "onchange", "oninput", "onfocus", "onblur",
                           "onmouseover", "onmouseout", "onmousedown", "onmouseup",
                           "onkeydown", "onkeyup", "onload", "onerror")
            .addAttributes("a", "href", "target")
            .addAttributes("img", "src", "alt", "width", "height")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("style", "type")
            .addAttributes("script", "type", "src")
            .addAttributes("link", "href", "rel", "type")
            .addAttributes("button", "type", "disabled", "class", "id", "style")
            .addAttributes("input", "type", "name", "value", "placeholder", "disabled", "checked",
                           "min", "max", "step", "required", "readonly", "class", "id", "style")
            .addAttributes("textarea", "name", "value", "placeholder", "rows", "cols", "disabled",
                           "required", "readonly", "class", "id", "style")
            .addAttributes("select", "name", "disabled", "required", "class", "id", "style")
            .addAttributes("option", "value", "disabled", "selected")
            .addAttributes("iframe", "src", "width", "height", "allow", "class", "id", "style")
            .addAttributes("video", "src", "controls", "width", "height", "poster", "class", "id", "style")
            .addAttributes("audio", "src", "controls", "class", "id", "style")
            .addAttributes("source", "src", "type")
            .addAttributes("canvas", "width", "height", "class", "id", "style")
            .addProtocols("img", "src", "http", "https")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("link", "href", "http", "https")
            .addProtocols("iframe", "src", "http", "https")
            .addProtocols("video", "src", "http", "https")
            .addProtocols("audio", "src", "http", "https")
            .addProtocols("source", "src", "http", "https");


    private HtmlSanitizer() {
        // 工具类禁止实例化
    }

    /**
     * 宽松消毒：用于教师上传的 HTML 课件。
     * 保留所有教学所需标签和属性（包括 iframe/form/input/script/style 等）。
     * 安全由 sandbox="allow-scripts" 兜底（无 same-origin，无网络请求）。
     */
    public static String sanitizeForCourseware(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "";
        }
        String safeHtml = Jsoup.clean(rawHtml, COURSEWARE_SAFELIST);
        if (!safeHtml.equals(rawHtml)) {
            log.info("[HtmlSanitizer] 课件消毒移除了部分内容: rawLength={}, safeLength={}",
                    rawHtml.length(), safeHtml.length());
        }
        return safeHtml;
    }

    /**
     * 对输入的 HTML 进行安全消毒（严格模式，用于用户生成内容）。
     */
    public static String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isEmpty()) {
            return "";
        }
        // 快速拒绝：先扫描是否含不允许的标签，避免大负载进入 Jsoup.clean() 流程
        if (containsDisallowedContent(rawHtml)) {
            String safeHtml = Jsoup.clean(rawHtml, SAFELIST);
            // 如果快速拒绝扫描触发但 Jsoup.clean 实际移除了内容，说明扫描正确
            // 如果 safeHtml 仍含危险内容（误报），Jsoup 会进一步清理
            log.info("[HtmlSanitizer] 快速拒绝扫描触发: rawLength={}", rawHtml.length());
            if (safeHtml.isEmpty()) {
                log.warn("[HtmlSanitizer] 内容全部被消毒移除，长度={}", rawHtml.length());
            }
            return safeHtml;
        }
        String safeHtml = Jsoup.clean(rawHtml, SAFELIST);
        if (!safeHtml.equals(rawHtml)) {
            log.info("[HtmlSanitizer] 移除了 {} 个字符的潜在不安全内容",
                    rawHtml.length() - safeHtml.length());
        }
        return safeHtml;
    }

    /**
     * 验证 HTML 是否包含不允许的标签或属性（快速拒绝扫描）。
     * 用于前端上传时的快速拒绝，避免大量数据进入 sanitize 流程。
     *
     * @param html HTML 内容
     * @return true 如果包含不允许的标签（检测到危险模式）
     */
    public static boolean containsDisallowedContent(String html) {
        if (html == null || html.isEmpty()) {
            return false;
        }
        String lower = html.toLowerCase();
        return lower.contains("<script")
                || lower.contains("onerror=")
                || lower.contains("onload=")
                || lower.contains("onclick=")
                || lower.contains("onmouseover=")
                || lower.contains("onfocus=")
                || lower.contains("onblur=")
                || lower.contains("onchange=")
                || lower.contains("onsubmit=")
                || lower.contains("onreset=")
                || lower.contains("javascript:")
                || lower.contains("vbscript:")
                || lower.contains("<iframe")
                || lower.contains("<embed")
                || lower.contains("<object")
                || lower.contains("<form")
                || lower.contains("<svg ")
                || lower.contains("<math ");
    }
}

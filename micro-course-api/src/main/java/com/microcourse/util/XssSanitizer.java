package com.microcourse.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * P1 安全修复: XSS 净化工具类。
 * 使用 Jsoup 替代正则 {@code <[^>]*>}，防止绕过。
 *
 * <p>Safelist: 允许基本格式化（b/i/u/strong/em）+ 链接（a href） + 块级（p/br/ul/ol/li）+ 图片（img src）。
 * 所有属性仅允许安全子集（href 自动过滤 javascript: 协议）。
 */
public final class XssSanitizer {

    private static final Safelist SAFELIST = Safelist.basic()
            .addTags("img", "span", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                    "blockquote", "pre", "code", "table", "thead", "tbody", "tr", "th", "td",
                    "hr", "sub", "sup", "del", "ins", "mark")
            .addAttributes("img", "src", "alt", "title", "width", "height")
            .addAttributes("a", "target", "rel")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("span", "style")
            .addAttributes("div", "style")
            .addAttributes("p", "style")
            .addProtocols("img", "src", "http", "https", "data")
            .addProtocols("a", "href", "http", "https", "mailto");

    private static final Safelist STRICT_SAFELIST = Safelist.none();

    private XssSanitizer() {
        // 工具类，禁止实例化
    }

    /**
     * 净化 HTML 内容，保留安全标签和属性。
     *
     * @param input 原始输入，可为 null
     * @return 净化后的字符串；input 为 null 时返回 null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, SAFELIST);
    }

    /**
     * 严格净化：移除所有 HTML 标签，仅保留纯文本。
     * 适用于通知标题、课程标题等不应包含 HTML 的字段。
     *
     * @param input 原始输入，可为 null
     * @return 纯文本字符串；input 为 null 时返回 null
     */
    public static String sanitizePlainText(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, STRICT_SAFELIST);
    }
}

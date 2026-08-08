package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SegmentInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * HTML 课件自动分段检测服务（P2-1，设计文档 §6.2）。
 *
 * <p>启发式算法（纯 Jsoup，无外部依赖）：
 * <ul>
 *   <li><b>标题元素</b>（h1-h6）开启新段：每个标题 + 其后的段落内容 = 一个 segment</li>
 *   <li><b>块边界</b>（section/article）开启新段，其内部全部内容并入该段（不重复细分）</li>
 *   <li><b>纯容器</b>（div/span/header/footer/main/aside/nav 等）不产生文本、仅递归子元素</li>
 *   <li><b>其余内容元素</b>（p/li/blockquote/pre 等）归入当前段；无当前段时自成一新段</li>
 *   <li>无任何结构时兜底：整个文档算 1 段（保证 detected_segments 至少可感知）</li>
 * </ul>
 *
 * <p>输出：segment_index（1 基）、marker（seg-N）、selector（#seg-N）、text（段文本摘要 ≤100 字符）。
 * 段落数上限 50：超过截断前 50 并告警（设计文档约束：1-50 段）。
 */
@Component
public class HtmlSegmentDetector {

    private static final Logger log = LoggerFactory.getLogger(HtmlSegmentDetector.class);

    /** 段落数上限（设计文档：1-50 段，超过警告教师） */
    public static final int MAX_SEGMENTS = 50;

    /** 段文本摘要长度 */
    private static final int TEXT_SUMMARY_LENGTH = 100;

    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> BLOCK_BOUNDARY_TAGS = Set.of("section", "article");
    private static final Set<String> CONTAINER_TAGS = Set.of(
            "div", "span", "header", "footer", "main", "aside", "nav", "figure", "figcaption", "body");
    /** 不可见/不可分段的内容标签（不计入文本，不参与分段） */
    private static final Set<String> IGNORED_TAGS = Set.of("script", "style", "noscript", "template", "head");

    /**
     * 检测 HTML 中的语义分段。
     *
     * @param htmlSanitized 已 sanitize 的 HTML（或原始 HTML）
     * @return 段列表（1-50 条，空/不可解析时为空列表）
     */
    public List<SegmentInfo> detectSegments(String htmlSanitized) {
        List<SegmentInfo> segments = new ArrayList<>();
        if (htmlSanitized == null || htmlSanitized.isBlank()) {
            return segments;
        }
        Document doc = Jsoup.parse(htmlSanitized);
        Element body = doc.body();
        if (body == null) {
            return segments;
        }
        SegmentBuilder current = null;
        current = walk(body.children(), current, segments);
        if (current != null && current.hasContent()) {
            segments.add(current.build(segments.size() + 1));
        }
        // 兜底：有可见文本但无任何可识别结构 → 整体 1 段
        if (segments.isEmpty()) {
            String visibleText = plainText(htmlSanitized);
            if (visibleText != null && !visibleText.isBlank()) {
                segments.add(new SegmentInfo(1, "seg-1", "#seg-1", visibleText));
            }
        }
        if (segments.size() > MAX_SEGMENTS) {
            log.warn("[HtmlSegmentDetector] 检测到 {} 段，超过上限 {}，截断前 {} 段（建议教师拆分课件）",
                    segments.size(), MAX_SEGMENTS, MAX_SEGMENTS);
            return new ArrayList<>(segments.subList(0, MAX_SEGMENTS));
        }
        return segments;
    }

    /**
     * 预序遍历（文档顺序）。返回遍历后"最后活跃的段构建器"，
     * 使嵌套（div > h2 > p）中的新段能向上传递，不被外层覆盖。
     *
     * 段边界规则（与设计文档 P2-1 一致）：
     * - h1-h6 开启新段：标题 + 其后段落 = 一个 segment
     * - section/article 开启新段：内部全部内容并入该段（不重复细分）
     * - 无活跃段时的块级内容元素（p/li/blockquote/...）自成一新段（独立段落边界）
     * - 活跃段内（标题之后）的块级内容归入当前段（"每个标题 + 后续段落 = 一个 segment"）
     * - script/style 等不可见内容完全忽略
     */
    private SegmentBuilder walk(List<Element> children, SegmentBuilder current, List<SegmentInfo> out) {
        for (Element child : children) {
            String tag = child.tagName().toLowerCase();
            if (IGNORED_TAGS.contains(tag)) {
                continue;
            }
            if (HEADING_TAGS.contains(tag)) {
                // 标题 → 完成当前段，开启新段（标题文本作段首）
                if (current != null && current.hasContent()) {
                    out.add(current.build(out.size() + 1));
                }
                current = new SegmentBuilder(child.text());
                current = walk(child.children(), current, out);
            } else if (BLOCK_BOUNDARY_TAGS.contains(tag)) {
                // section/article → 完成当前段，开启新段并吞入内部全部内容
                if (current != null && current.hasContent()) {
                    out.add(current.build(out.size() + 1));
                }
                current = new SegmentBuilder(child.text());
            } else if (CONTAINER_TAGS.contains(tag)) {
                // 纯容器 → 不产生文本，仅递归子元素
                current = walk(child.children(), current, out);
            } else {
                // 块级内容元素（p/li/blockquote/pre/...）
                if (current == null) {
                    // 无活跃段 → 该内容自成一新段（p 即段边界）
                    SegmentBuilder solo = new SegmentBuilder(child.text());
                    out.add(solo.build(out.size() + 1));
                } else {
                    // 活跃段内（标题之后）→ 归入当前段
                    current.append(child.text());
                    current = walk(child.children(), current, out);
                }
            }
        }
        return current;
    }

    /**
     * 提取可见文本（移除 script/style/noscript 内容——Jsoup 默认 text() 会包含它们）。
     */
    private String plainText(String html) {
        try {
            Document d = Jsoup.parse(html);
            d.select("script,style,noscript").remove();
            String t = d.text();
            return t == null ? "" : t;
        } catch (Exception e) {
            return "";
        }
    }

    /** 段构建器：累积段文本，落库时生成 index/marker/selector/text。 */
    private static final class SegmentBuilder {
        private final StringBuilder text = new StringBuilder();

        SegmentBuilder(String initialText) {
            if (initialText != null && !initialText.isBlank()) {
                text.append(initialText);
            }
        }

        void append(String chunk) {
            if (chunk == null || chunk.isBlank()) {
                return;
            }
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(chunk.trim());
        }

        boolean hasContent() {
            return !text.isEmpty();
        }

        SegmentInfo build(int index) {
            String full = text.toString().trim();
            String summary = full.length() <= TEXT_SUMMARY_LENGTH
                    ? full : full.substring(0, TEXT_SUMMARY_LENGTH);
            return new SegmentInfo(index, "seg-" + index, "#seg-" + index, summary);
        }
    }
}

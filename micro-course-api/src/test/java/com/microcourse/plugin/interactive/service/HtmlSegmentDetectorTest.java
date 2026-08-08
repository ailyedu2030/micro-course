package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SegmentInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HtmlSegmentDetector 单测（P2-1 自动分段检测）。
 * 覆盖：标题+段落、列表、嵌套 section、纯容器、空/无结构兜底、50 段上限。
 */
class HtmlSegmentDetectorTest {

    private final HtmlSegmentDetector detector = new HtmlSegmentDetector();

    @Nested
    @DisplayName("标题+段落结构")
    class HeadingParagraph {
        @Test
        @DisplayName("h1 + p 归为一段，h2 + p 归为另一段")
        void headingThenParagraph() {
            String html = "<h1>第一章 引言</h1><p>这是第一节内容。</p>"
                    + "<h2>1.1 背景</h2><p>这是第二节内容。</p>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(2, segs.size());
            assertEquals(1, segs.get(0).getIndex());
            assertEquals("seg-1", segs.get(0).getMarker());
            assertTrue(segs.get(0).getText().contains("第一章"));
            assertEquals(2, segs.get(1).getIndex());
            assertEquals("seg-2", segs.get(1).getMarker());
            assertTrue(segs.get(1).getText().contains("1.1"));
        }

        @Test
        @DisplayName("标题后的列表项归入该段")
        void headingWithList() {
            String html = "<h3>需求清单</h3><ul><li>支持上传</li><li>支持替换</li></ul>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(1, segs.size());
            assertTrue(segs.get(0).getText().contains("需求清单"));
            assertTrue(segs.get(0).getText().contains("支持上传"));
        }

        @Test
        @DisplayName("h1/h2/h3 连续标题各自成段")
        void multipleHeadings() {
            String html = "<h1>A</h1><h2>B</h2><h3>C</h3><p>正文</p>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(3, segs.size());
            assertEquals("seg-1", segs.get(0).getMarker());
            assertEquals("seg-2", segs.get(1).getMarker());
            assertEquals("seg-3", segs.get(2).getMarker());
            assertTrue(segs.get(2).getText().contains("正文"));
        }
    }

    @Nested
    @DisplayName("嵌套与块边界")
    class NestedAndBoundary {
        @Test
        @DisplayName("div 容器内 h2 + p 正确分段（嵌套不丢失段）")
        void nestedDiv() {
            String html = "<div class=\"wrapper\"><h2>小节一</h2><p>内容一</p></div>"
                    + "<div><h2>小节二</h2><p>内容二</p></div>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(2, segs.size());
            assertTrue(segs.get(0).getText().contains("小节一"));
            assertTrue(segs.get(1).getText().contains("小节二"));
        }

        @Test
        @DisplayName("section 作为整体一段（内部标题并入不重复细分）")
        void sectionAsBoundary() {
            String html = "<section><h3>区块标题</h3><p>区块内容</p></section>"
                    + "<section><h3>另一区块</h3><p>另一内容</p></section>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(2, segs.size());
            assertTrue(segs.get(0).getText().contains("区块"));
            assertTrue(segs.get(1).getText().contains("另一区块"));
        }

        @Test
        @DisplayName("无标题的多个独立 p 各自成段")
        void plainParagraphs() {
            String html = "<p>第一段</p><p>第二段</p><p>第三段</p>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(3, segs.size());
            assertEquals("seg-1", segs.get(0).getMarker());
            assertEquals("seg-3", segs.get(2).getMarker());
        }
    }

    @Nested
    @DisplayName("边界与限制")
    class EdgeCases {
        @Test
        @DisplayName("null / 空白 / 纯标签 返回空列表")
        void emptyInputs() {
            assertTrue(detector.detectSegments(null).isEmpty());
            assertTrue(detector.detectSegments("").isEmpty());
            assertTrue(detector.detectSegments("   ").isEmpty());
            assertTrue(detector.detectSegments("<div><script>alert(1)</script></div>").isEmpty());
        }

        @Test
        @DisplayName("无结构但有文本时兜底为 1 段")
        void fallbackSingleSegment() {
            List<SegmentInfo> segs = detector.detectSegments("纯文本内容");
            assertEquals(1, segs.size());
            assertEquals(1, segs.get(0).getIndex());
        }

        @Test
        @DisplayName("超过 50 段时截断为前 50 段")
        void capAtFifty() {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 60; i++) {
                sb.append("<h2>标题").append(i).append("</h2><p>内容").append(i).append("</p>");
            }
            List<SegmentInfo> segs = detector.detectSegments(sb.toString());
            assertEquals(HtmlSegmentDetector.MAX_SEGMENTS, segs.size());
            assertEquals("seg-1", segs.get(0).getMarker());
            assertEquals("seg-50", segs.get(49).getMarker());
        }

        @Test
        @DisplayName("文本摘要不超过 100 字符")
        void textSummaryTruncated() {
            StringBuilder longText = new StringBuilder();
            for (int i = 0; i < 30; i++) longText.append("很长的内容文本 ");
            String html = "<p>" + longText + "</p>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            assertEquals(1, segs.size());
            assertTrue(segs.get(0).getText().length() <= 100);
        }

        @Test
        @DisplayName("marker 与 selector 格式正确（seg-N / #seg-N）")
        void markerAndSelectorFormat() {
            String html = "<h1>标题</h1><p>内容</p><h2>第二个</h2>";
            List<SegmentInfo> segs = detector.detectSegments(html);
            for (SegmentInfo s : segs) {
                assertEquals("seg-" + s.getIndex(), s.getMarker());
                assertEquals("#seg-" + s.getIndex(), s.getSelector());
            }
        }
    }
}

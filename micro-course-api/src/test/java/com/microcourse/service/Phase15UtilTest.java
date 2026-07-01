package com.microcourse.service;

import com.microcourse.util.WordCountUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15: 工具类单元测试（纯逻辑，无需Spring上下文）。
 */
class Phase15UtilTest {

    @Test
    @DisplayName("WordCountUtil: 空字符串返回0")
    void testNullReturnsZero() {
        assertEquals(0, WordCountUtil.stripHtmlAndCount(null));
        assertEquals(0, WordCountUtil.stripHtmlAndCount(""));
        assertEquals(0, WordCountUtil.stripHtmlAndCount("   "));
    }

    @Test
    @DisplayName("WordCountUtil: 纯文本正确计数")
    void testPlainText() {
        assertEquals(4, WordCountUtil.stripHtmlAndCount("你好世界"));
    }

    @Test
    @DisplayName("WordCountUtil: HTML标签不计入字数")
    void testHtmlTagsStripped() {
        String html = "<p>你好世界</p>";
        assertEquals(4, WordCountUtil.stripHtmlAndCount(html));
    }

    @Test
    @DisplayName("WordCountUtil: 带属性的HTML标签")
    void testHtmlWithAttributes() {
        String html = "<div class=\"content\"><p>测试文本</p></div>";
        assertEquals(4, WordCountUtil.stripHtmlAndCount(html));
    }

    @Test
    @DisplayName("WordCountUtil: HTML实体被正确处理")
    void testHtmlEntities() {
        String html = "<p>&nbsp;你好&nbsp;世界</p>";
        assertEquals(4, WordCountUtil.stripHtmlAndCount(html));
    }

    @Test
    @DisplayName("WordCountUtil: 超过建议字数预警")
    void testOverRecommend() {
        String text = "<p>" + "字".repeat(501) + "</p>";
        assertTrue(WordCountUtil.isOverRecommend(text, 500));
        assertFalse(WordCountUtil.isOverRecommend(text, 600));
    }

    @Test
    @DisplayName("WordCountUtil: 超过预警字数检测")
    void testOverWarning() {
        String text = "<p>" + "字".repeat(601) + "</p>";
        assertTrue(WordCountUtil.isOverWarning(text, 600));
    }
}

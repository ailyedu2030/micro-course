package com.microcourse.util;

/**
 * 字数统计工具
 * 去除 HTML 标签后统计纯文本字数
 */
public final class WordCountUtil {

    private WordCountUtil() {}

    /**
     * 去除 HTML 标签后统计纯文本字数
     */
    public static int stripHtmlAndCount(String html) {
        if (html == null || html.isBlank()) {
            return 0;
        }
        return html.replaceAll("<[^>]*>", "")
                   .replaceAll("&\\w+;", " ")
                   .replaceAll("\\s+", "")
                   .length();
    }

    /**
     * 检查是否超过建议字数
     */
    public static boolean isOverRecommend(String html, int recommend) {
        return stripHtmlAndCount(html) > recommend;
    }

    /**
     * 检查是否超过预警字数
     */
    public static boolean isOverWarning(String html, int warning) {
        return stripHtmlAndCount(html) > warning;
    }
}

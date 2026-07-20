package com.microcourse.plugin.interactive.adapter;

/**
 * 课件分段内容 (图片 URL 或 HTML 内容)
 */
public interface SegmentContent {
    String getContentType();  // "IMAGE" | "HTML"
    String getImageUrl();     // contentType=IMAGE
    Integer getImageWidth();
    Integer getImageHeight();
    String getHtmlContent();  // contentType=HTML (sanitized)
}
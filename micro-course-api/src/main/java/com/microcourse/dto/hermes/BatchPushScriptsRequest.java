package com.microcourse.dto.hermes;

/**
 * 批量推送课件脚本请求 DTO（替代 Map&lt;String, Object&gt;）。
 *
 * <p>对应 POST /api/hermes/webhook/courses/{hermesCourseId}/scripts 端点</p>
 */
public class BatchPushScriptsRequest {

    private String scriptContent;
    private Long sectionId;
    private Long chapterId;

    public String getScriptContent() { return scriptContent; }
    public void setScriptContent(String scriptContent) { this.scriptContent = scriptContent; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
}

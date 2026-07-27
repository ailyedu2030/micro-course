package com.microcourse.dto.hermes;

/**
 * 课件页面旁白更新请求 DTO（替代 Map&lt;String, Object&gt;）。
 *
 * <p>对应 PATCH /api/hermes/webhook/courses/{hermesCourseId}/lessons/{lessonId}/slides/pages/{pageNumber} 端点</p>
 */
public class NarrationUpdateRequest {

    /** 旁白脚本内容 */
    private String narrationScript;

    public String getNarrationScript() { return narrationScript; }
    public void setNarrationScript(String narrationScript) { this.narrationScript = narrationScript; }
}

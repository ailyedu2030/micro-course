package com.microcourse.plugin.interactive.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Courseware 读侧统一 DTO (CQRS Query 模式).
 * <p>
 * 前端调用 {@code GET /api/courses/{cid}/courseware/{sid}} 直接拿到课件树,
 * 无需在前端组合多个分页请求, 提升首屏加载速度.
 * </p>
 *
 * <ul>
 *   <li>type = "PPT" 时 pages 字段填充, htmlUnit 为 null</li>
 *   <li>type = "HTML" 时 htmlUnit 字段填充, pages 为 null</li>
 *   <li>type = "LEGACY" 表示旧 slide_pages, 走 legacy 路径</li>
 *   <li>type = "EMPTY" 表示该 section 没有课件</li>
 * </ul>
 */
public class CoursewareTreeDTO {
    private String type;
    private Long sectionId;
    private Long courseId;

    // PPT 课件用
    private List<PptPageNode> pages;
    private List<PptFlowDTO> flow;

    // HTML 课件用
    private SlideHtmlUnitDTO htmlUnit;

    // 状态聚合 (来自视图 v_slide_ppt_page_status / v_slide_html_unit_status)
    private String narrationStatus;
    private Integer audioReadyCount;

    private LocalDateTime lastUpdatedAt;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public List<PptPageNode> getPages() { return pages; }
    public void setPages(List<PptPageNode> pages) { this.pages = pages; }
    public List<PptFlowDTO> getFlow() { return flow; }
    public void setFlow(List<PptFlowDTO> flow) { this.flow = flow; }
    public SlideHtmlUnitDTO getHtmlUnit() { return htmlUnit; }
    public void setHtmlUnit(SlideHtmlUnitDTO htmlUnit) { this.htmlUnit = htmlUnit; }
    public String getNarrationStatus() { return narrationStatus; }
    public void setNarrationStatus(String narrationStatus) { this.narrationStatus = narrationStatus; }
    public Integer getAudioReadyCount() { return audioReadyCount; }
    public void setAudioReadyCount(Integer audioReadyCount) { this.audioReadyCount = audioReadyCount; }
    public LocalDateTime getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    /**
     * PPT 单页节点 (含 page 元数据 + 脚本 + 音频列表).
     */
    public static class PptPageNode {
        private Long pageId;
        private Integer pageNumber;
        private String pageTitle;
        private String imageUrl;
        private String thumbnailUrl;
        private PptScriptDTO activeScript;
        private List<PptAudioDTO> audios;
        private String narrationStatus;

        public Long getPageId() { return pageId; }
        public void setPageId(Long pageId) { this.pageId = pageId; }
        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
        public String getPageTitle() { return pageTitle; }
        public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getThumbnailUrl() { return thumbnailUrl; }
        public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }
        public PptScriptDTO getActiveScript() { return activeScript; }
        public void setActiveScript(PptScriptDTO activeScript) { this.activeScript = activeScript; }
        public List<PptAudioDTO> getAudios() { return audios; }
        public void setAudios(List<PptAudioDTO> audios) { this.audios = audios; }
        public String getNarrationStatus() { return narrationStatus; }
        public void setNarrationStatus(String narrationStatus) { this.narrationStatus = narrationStatus; }
    }
}
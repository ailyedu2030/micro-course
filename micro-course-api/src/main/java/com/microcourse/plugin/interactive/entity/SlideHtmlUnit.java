package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * HTML 课件单元实体 (slide_html_units 表, V303).
 * 一个 section 最多 1 个 HTML 单元 (uk_html_units_section UNIQUE).
 */
@TableName("slide_html_units")
public class SlideHtmlUnit {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private Long courseId;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("section_id")
    private Long sectionId;

    @TableField("slide_id")
    private Long slideId;

    @TableField("page_title")
    private String pageTitle;

    @TableField("file_uuid")
    private String fileUuid;

    @TableField("html_content")
    private String htmlContent;

    @TableField("html_sanitized")
    private String htmlSanitized;

    @TableField("file_size_bytes")
    private Long fileSizeBytes;

    @TableField("detected_segments")
    private Integer detectedSegments;

    @TableField("has_interactions")
    private Boolean hasInteractions;

    @TableField("interaction_types")
    private String interactionTypes;  // JSONB

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public SlideHtmlUnit() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Long getSlideId() { return slideId; }
    public void setSlideId(Long slideId) { this.slideId = slideId; }
    public String getPageTitle() { return pageTitle; }
    public void setPageTitle(String pageTitle) { this.pageTitle = pageTitle; }
    public String getFileUuid() { return fileUuid; }
    public void setFileUuid(String fileUuid) { this.fileUuid = fileUuid; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public String getHtmlSanitized() { return htmlSanitized; }
    public void setHtmlSanitized(String htmlSanitized) { this.htmlSanitized = htmlSanitized; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Integer getDetectedSegments() { return detectedSegments; }
    public void setDetectedSegments(Integer detectedSegments) { this.detectedSegments = detectedSegments; }
    public Boolean getHasInteractions() { return hasInteractions; }
    public void setHasInteractions(Boolean hasInteractions) { this.hasInteractions = hasInteractions; }
    public String getInteractionTypes() { return interactionTypes; }
    public void setInteractionTypes(String interactionTypes) { this.interactionTypes = interactionTypes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
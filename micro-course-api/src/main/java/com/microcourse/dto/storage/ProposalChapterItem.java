package com.microcourse.dto.storage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 章节 DTO (proposal_chapters 表对应)
 * 用于 Phase 1: 申报表加章节支持
 */
public class ProposalChapterItem {

    private Long id;

    @NotBlank(message = "章节名称不能为空")
    @Size(max = 200, message = "章节名称不能超过200个字符")
    private String title;

    @Size(max = 1000, message = "章节描述不能超过1000个字符")
    private String description;

    @Min(value = 0, message = "学时不能为负数")
    private Integer hours;

    private Integer sortOrder;

    public ProposalChapterItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}

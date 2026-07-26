package com.microcourse.dto.interactive;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 互动课件更新请求 DTO
 * <p>替换 Controller 中的 Map<String, Object>，提供类型安全和参数校验。</p>
 */
public class SlideUpdateRequest {

    @NotNull(message = "课件 ID 不能为空")
    private Long id;

    @Size(max = 200, message = "标题长度不能超过 200 个字符")
    private String title;

    private String description;

    private Integer sortOrder;

    private Long chapterId;

    /** 课件文件名（可更新） */
    private String fileName;

    public SlideUpdateRequest() {
    }

    @NotNull
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Size(max = 200)
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}

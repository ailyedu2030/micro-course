package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("section_tasks")
public class SectionTask {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("section_id") private Long sectionId;
    private Integer slide;
    private String description;
    @TableField("created_at") private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Integer getSlide() { return slide; }
    public void setSlide(Integer slide) { this.slide = slide; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("section_reflections")
public class SectionReflection {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("section_id") private Long sectionId;
    private String template;
    @TableField("created_at") private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

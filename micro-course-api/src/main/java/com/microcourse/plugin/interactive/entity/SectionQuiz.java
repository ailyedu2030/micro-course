package com.microcourse.plugin.interactive.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("section_quizzes")
public class SectionQuiz {
    @TableId(type = IdType.AUTO) private Long id;
    @TableField("section_id") private Long sectionId;
    private Integer slide;
    private String prompt;
    private String options; // JSONB
    @TableField("correct_index") private Integer correctIndex;
    private String explanation;
    @TableField("created_at") private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }
    public Integer getSlide() { return slide; }
    public void setSlide(Integer slide) { this.slide = slide; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public Integer getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(Integer correctIndex) { this.correctIndex = correctIndex; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

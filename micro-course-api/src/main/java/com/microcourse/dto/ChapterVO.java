package com.microcourse.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ChapterVO {

    private Long id;
    private Long courseId;
    private String title;
    private String description;
    private Integer sortOrder;
    @Deprecated
    private String chapterType;
    /** 从 course_sections.section_type 推导的章节类型（取第一个课时的类型） */
    private String sectionType;
    private Integer sectionCount;
    private Integer duration;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private String learningObjectives;
    private List<String> keyConcepts;
    private List<Object> exercises;
    private Integer videoCount;

    public ChapterVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    @Deprecated public String getChapterType() { return null; }
    @Deprecated public void setChapterType(String chapterType) { /* no-op */ }
    public String getSectionType() { return sectionType; }
    public void setSectionType(String sectionType) { this.sectionType = sectionType; }
    public Integer getSectionCount() { return sectionCount; }
    public void setSectionCount(Integer sectionCount) { this.sectionCount = sectionCount; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }
    public List<String> getKeyConcepts() { return keyConcepts; }
    public void setKeyConcepts(List<String> keyConcepts) { this.keyConcepts = keyConcepts; }
    public List<Object> getExercises() { return exercises; }
    public void setExercises(List<Object> exercises) { this.exercises = exercises; }
    public Integer getVideoCount() { return videoCount; }
    public void setVideoCount(Integer videoCount) { this.videoCount = videoCount; }

    // ===== P1 Stage 1: 章节级元信息(交叉审查 P1-1:VO 必须包含新字段) =====
    private Integer no;
    private String anchorPoint;
    private String coreQuestion;
    private Integer chapterHours;

    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getAnchorPoint() { return anchorPoint; }
    public void setAnchorPoint(String anchorPoint) { this.anchorPoint = anchorPoint; }
    public String getCoreQuestion() { return coreQuestion; }
    public void setCoreQuestion(String coreQuestion) { this.coreQuestion = coreQuestion; }
    public Integer getChapterHours() { return chapterHours; }
    public void setChapterHours(Integer chapterHours) { this.chapterHours = chapterHours; }
}

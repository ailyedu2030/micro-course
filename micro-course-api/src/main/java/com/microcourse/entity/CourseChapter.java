package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("course_chapters")
public class CourseChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("course_id")
    private Long courseId;

    private String title;
    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    /** @deprecated column removed in V186, type now at section level */
    @Deprecated
    @TableField(exist = false)
    private String chapterType;

    private Integer duration;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Integer version;

    @TableLogic(value = "NULL", delval = "now()")
    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("learning_objectives")
    private String learningObjectives;

    // ===== P1 Stage 1: 章节级元信息(Trae SKILL.md 模块 3.2) =====
    @TableField("no")
    private Integer no;

    @TableField("anchor_point")
    private String anchorPoint;

    @TableField("core_question")
    private String coreQuestion;

    @TableField("chapter_hours")
    private Integer chapterHours;

    public CourseChapter() {}

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
    public String getChapterType() { return chapterType; }
    public void setChapterType(String chapterType) { this.chapterType = chapterType; }
    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }

    // ===== P1 Stage 1 getters/setters =====
    public Integer getNo() { return no; }
    public void setNo(Integer no) { this.no = no; }
    public String getAnchorPoint() { return anchorPoint; }
    public void setAnchorPoint(String anchorPoint) { this.anchorPoint = anchorPoint; }
    public String getCoreQuestion() { return coreQuestion; }
    public void setCoreQuestion(String coreQuestion) { this.coreQuestion = coreQuestion; }
    public Integer getChapterHours() { return chapterHours; }
    public void setChapterHours(Integer chapterHours) { this.chapterHours = chapterHours; }
}
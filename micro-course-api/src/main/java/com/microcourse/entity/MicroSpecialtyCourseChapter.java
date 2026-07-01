package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("micro_specialty_course_chapters")
public class MicroSpecialtyCourseChapter {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("micro_specialty_id")
    private Long microSpecialtyId;

    @TableField("course_id")
    private Long courseId;

    @TableField("chapter_id")
    private Long chapterId;

    private String source;

    @TableField("proposal_chapter_id")
    private Long proposalChapterId;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public MicroSpecialtyCourseChapter() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMicroSpecialtyId() { return microSpecialtyId; }
    public void setMicroSpecialtyId(Long microSpecialtyId) { this.microSpecialtyId = microSpecialtyId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getProposalChapterId() { return proposalChapterId; }
    public void setProposalChapterId(Long proposalChapterId) { this.proposalChapterId = proposalChapterId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

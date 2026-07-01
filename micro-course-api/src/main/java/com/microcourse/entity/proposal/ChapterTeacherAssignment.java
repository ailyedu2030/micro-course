package com.microcourse.entity.proposal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 章节-教师映射 (chapter_teacher_assignments 表)
 * V107 migration
 */
@TableName("chapter_teacher_assignments")
public class ChapterTeacherAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("proposal_id")
    private Long proposalId;

    @TableField("course_id")
    private Long courseId;

    @TableField("chapter_id")
    private Long chapterId;

    @TableField("teacher_id")
    private Long teacherId;

    private String source;           // TBD | existing | new

    @TableField("source_course_id")
    private Long sourceCourseId;

    @TableField("source_chapter_id")
    private Long sourceChapterId;

    @TableField("accept_status")
    private String acceptStatus;     // PENDING | ACCEPTED | DECLINED | REVOKED | LEFT

    @TableField("accepted_at")
    private LocalDateTime acceptedAt;

    @TableField("frozen_price")
    private BigDecimal frozenPrice;

    private String responsibility;

    @Version
    private Integer version;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposalId() { return proposalId; }
    public void setProposalId(Long proposalId) { this.proposalId = proposalId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getSourceCourseId() { return sourceCourseId; }
    public void setSourceCourseId(Long sourceCourseId) { this.sourceCourseId = sourceCourseId; }
    public Long getSourceChapterId() { return sourceChapterId; }
    public void setSourceChapterId(Long sourceChapterId) { this.sourceChapterId = sourceChapterId; }
    public String getAcceptStatus() { return acceptStatus; }
    public void setAcceptStatus(String acceptStatus) { this.acceptStatus = acceptStatus; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public BigDecimal getFrozenPrice() { return frozenPrice; }
    public void setFrozenPrice(BigDecimal frozenPrice) { this.frozenPrice = frozenPrice; }
    public String getResponsibility() { return responsibility; }
    public void setResponsibility(String responsibility) { this.responsibility = responsibility; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

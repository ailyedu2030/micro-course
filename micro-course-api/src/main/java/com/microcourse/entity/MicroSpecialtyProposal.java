package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;

@TableName("micro_specialty_proposals")
public class MicroSpecialtyProposal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long proposerId;
    private String title;
    private String description;
    private Long offerDepartmentId;
    private String trainingObjective;
    private String prerequisites;
    private String semester;
    private Integer maxStudents;
    private java.math.BigDecimal credits;
    private String status;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long createdMicroSpecialtyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;

    public MicroSpecialtyProposal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProposerId() { return proposerId; }
    public void setProposerId(Long proposerId) { this.proposerId = proposerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }
    public String getTrainingObjective() { return trainingObjective; }
    public void setTrainingObjective(String trainingObjective) { this.trainingObjective = trainingObjective; }
    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public java.math.BigDecimal getCredits() { return credits; }
    public void setCredits(java.math.BigDecimal credits) { this.credits = credits; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public Long getCreatedMicroSpecialtyId() { return createdMicroSpecialtyId; }
    public void setCreatedMicroSpecialtyId(Long createdMicroSpecialtyId) { this.createdMicroSpecialtyId = createdMicroSpecialtyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

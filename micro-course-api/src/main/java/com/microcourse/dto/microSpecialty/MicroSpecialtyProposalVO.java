package com.microcourse.dto.microSpecialty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyProposalVO {
    private Long id; private Long proposerId; private String proposerName; private String title;
    private String description; private Long offerDepartmentId; private String departmentName;
    private String trainingObjective; private String semester; private Integer maxStudents;
    private String status; private String reviewComment; private Long reviewedBy;
    private String reviewedByName; private LocalDateTime reviewedAt;
    private Long createdMicroSpecialtyId; private LocalDateTime createdAt;
    public MicroSpecialtyProposalVO() {}
    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getProposerId() { return proposerId; } public void setProposerId(Long v) { proposerId = v; }
    public String getProposerName() { return proposerName; } public void setProposerName(String v) { proposerName = v; }
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public Long getOfferDepartmentId() { return offerDepartmentId; } public void setOfferDepartmentId(Long v) { offerDepartmentId = v; }
    public String getDepartmentName() { return departmentName; } public void setDepartmentName(String v) { departmentName = v; }
    public String getTrainingObjective() { return trainingObjective; } public void setTrainingObjective(String v) { trainingObjective = v; }
    public String getSemester() { return semester; } public void setSemester(String v) { semester = v; }
    public Integer getMaxStudents() { return maxStudents; } public void setMaxStudents(Integer v) { maxStudents = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getReviewComment() { return reviewComment; } public void setReviewComment(String v) { reviewComment = v; }
    public Long getReviewedBy() { return reviewedBy; } public void setReviewedBy(Long v) { reviewedBy = v; }
    public String getReviewedByName() { return reviewedByName; } public void setReviewedByName(String v) { reviewedByName = v; }
    public LocalDateTime getReviewedAt() { return reviewedAt; } public void setReviewedAt(LocalDateTime v) { reviewedAt = v; }
    public Long getCreatedMicroSpecialtyId() { return createdMicroSpecialtyId; } public void setCreatedMicroSpecialtyId(Long v) { createdMicroSpecialtyId = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt = v; }
}

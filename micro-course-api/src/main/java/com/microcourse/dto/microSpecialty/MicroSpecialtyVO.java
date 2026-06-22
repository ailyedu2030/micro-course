package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyVO {
    private Long id; private String code; private String title; private String subtitle;
    private String coverUrl; private String description; private Long offerDepartmentId;
    private String departmentName; private Long leadTeacherId; private String leadTeacherName;
    private String targetAudience; private String trainingObjective; private String admissionRequirement;
    private String completionRule; private BigDecimal totalCredits; private Integer totalHours;
    private Integer requiredCourseCount; private Integer electiveCourseCount; private BigDecimal minCredits;
    private Integer maxStudents; private Integer studentCount; private String semester;
    private Boolean isFeatured; private Integer featuredRank; private String featuredStatus;
    private Boolean isGoldFeatured; private String status; private String rejectReason;
    private LocalDateTime submittedAt; private LocalDateTime approvedAt; private LocalDateTime openedAt;
    private LocalDateTime closedAt; private Long creatorId; private String creatorName;
    private LocalDateTime createdAt;

    public MicroSpecialtyVO() {}
    public Long getId() { return id; } public void setId(Long v) { this.id = v; }
    public String getCode() { return code; } public void setCode(String v) { this.code = v; }
    public String getTitle() { return title; } public void setTitle(String v) { this.title = v; }
    public String getSubtitle() { return subtitle; } public void setSubtitle(String v) { this.subtitle = v; }
    public String getCoverUrl() { return coverUrl; } public void setCoverUrl(String v) { this.coverUrl = v; }
    public String getDescription() { return description; } public void setDescription(String v) { this.description = v; }
    public Long getOfferDepartmentId() { return offerDepartmentId; } public void setOfferDepartmentId(Long v) { this.offerDepartmentId = v; }
    public String getDepartmentName() { return departmentName; } public void setDepartmentName(String v) { this.departmentName = v; }
    public Long getLeadTeacherId() { return leadTeacherId; } public void setLeadTeacherId(Long v) { this.leadTeacherId = v; }
    public String getLeadTeacherName() { return leadTeacherName; } public void setLeadTeacherName(String v) { this.leadTeacherName = v; }
    public String getTargetAudience() { return targetAudience; } public void setTargetAudience(String v) { this.targetAudience = v; }
    public String getTrainingObjective() { return trainingObjective; } public void setTrainingObjective(String v) { this.trainingObjective = v; }
    public String getAdmissionRequirement() { return admissionRequirement; } public void setAdmissionRequirement(String v) { this.admissionRequirement = v; }
    public String getCompletionRule() { return completionRule; } public void setCompletionRule(String v) { this.completionRule = v; }
    public BigDecimal getTotalCredits() { return totalCredits; } public void setTotalCredits(BigDecimal v) { this.totalCredits = v; }
    public Integer getTotalHours() { return totalHours; } public void setTotalHours(Integer v) { this.totalHours = v; }
    public Integer getRequiredCourseCount() { return requiredCourseCount; } public void setRequiredCourseCount(Integer v) { this.requiredCourseCount = v; }
    public Integer getElectiveCourseCount() { return electiveCourseCount; } public void setElectiveCourseCount(Integer v) { this.electiveCourseCount = v; }
    public BigDecimal getMinCredits() { return minCredits; } public void setMinCredits(BigDecimal v) { this.minCredits = v; }
    public Integer getMaxStudents() { return maxStudents; } public void setMaxStudents(Integer v) { this.maxStudents = v; }
    public Integer getStudentCount() { return studentCount; } public void setStudentCount(Integer v) { this.studentCount = v; }
    public String getSemester() { return semester; } public void setSemester(String v) { this.semester = v; }
    public Boolean getIsFeatured() { return isFeatured; } public void setIsFeatured(Boolean v) { this.isFeatured = v; }
    public Integer getFeaturedRank() { return featuredRank; } public void setFeaturedRank(Integer v) { this.featuredRank = v; }
    public String getFeaturedStatus() { return featuredStatus; } public void setFeaturedStatus(String v) { this.featuredStatus = v; }
    public Boolean getIsGoldFeatured() { return isGoldFeatured; } public void setIsGoldFeatured(Boolean v) { this.isGoldFeatured = v; }
    public String getStatus() { return status; } public void setStatus(String v) { this.status = v; }
    public String getRejectReason() { return rejectReason; } public void setRejectReason(String v) { this.rejectReason = v; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime v) { this.submittedAt = v; }
    public LocalDateTime getApprovedAt() { return approvedAt; } public void setApprovedAt(LocalDateTime v) { this.approvedAt = v; }
    public LocalDateTime getOpenedAt() { return openedAt; } public void setOpenedAt(LocalDateTime v) { this.openedAt = v; }
    public LocalDateTime getClosedAt() { return closedAt; } public void setClosedAt(LocalDateTime v) { this.closedAt = v; }
    public Long getCreatorId() { return creatorId; } public void setCreatorId(Long v) { this.creatorId = v; }
    public String getCreatorName() { return creatorName; } public void setCreatorName(String v) { this.creatorName = v; }
    public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}

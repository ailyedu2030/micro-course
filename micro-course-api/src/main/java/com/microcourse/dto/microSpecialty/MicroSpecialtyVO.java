package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyVO {

    private Long id;
    private String code;
    private String title;
    private String subtitle;
    private String coverUrl;
    private String description;
    private Long offerDepartmentId;
    private String departmentName;
    private Long leadTeacherId;
    private String leadTeacherName;
    private String targetAudience;
    private String trainingObjective;
    private String admissionRequirement;
    private String completionRule;
    private BigDecimal totalCredits;
    private Integer totalHours;
    private Integer requiredCourseCount;
    private Integer electiveCourseCount;
    private BigDecimal minCredits;
    private Integer maxStudents;
    private Integer studentCount;
    private String semester;
    private Boolean isFeatured;
    private Integer featuredRank;
    private String featuredStatus;
    private Boolean isGoldFeatured;
    private String status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime createdAt;
    private LocalDateTime featuredApplyAt;
    private String featuredApplyReason;
    private LocalDateTime updatedAt;
    private Integer pendingEnrollCount;
    private Integer courseCount;
    private String role;
    private Integer totalEnrollments;

    public MicroSpecialtyVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public Long getLeadTeacherId() { return leadTeacherId; }
    public void setLeadTeacherId(Long leadTeacherId) { this.leadTeacherId = leadTeacherId; }

    public String getLeadTeacherName() { return leadTeacherName; }
    public void setLeadTeacherName(String leadTeacherName) { this.leadTeacherName = leadTeacherName; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public String getTrainingObjective() { return trainingObjective; }
    public void setTrainingObjective(String trainingObjective) { this.trainingObjective = trainingObjective; }

    public String getAdmissionRequirement() { return admissionRequirement; }
    public void setAdmissionRequirement(String admissionRequirement) { this.admissionRequirement = admissionRequirement; }

    public String getCompletionRule() { return completionRule; }
    public void setCompletionRule(String completionRule) { this.completionRule = completionRule; }

    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }

    public Integer getTotalHours() { return totalHours; }
    public void setTotalHours(Integer totalHours) { this.totalHours = totalHours; }

    public Integer getRequiredCourseCount() { return requiredCourseCount; }
    public void setRequiredCourseCount(Integer requiredCourseCount) { this.requiredCourseCount = requiredCourseCount; }

    public Integer getElectiveCourseCount() { return electiveCourseCount; }
    public void setElectiveCourseCount(Integer electiveCourseCount) { this.electiveCourseCount = electiveCourseCount; }

    public BigDecimal getMinCredits() { return minCredits; }
    public void setMinCredits(BigDecimal minCredits) { this.minCredits = minCredits; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public Boolean getIsFeatured() { return isFeatured; }
    public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }

    public Integer getFeaturedRank() { return featuredRank; }
    public void setFeaturedRank(Integer featuredRank) { this.featuredRank = featuredRank; }

    public String getFeaturedStatus() { return featuredStatus; }
    public void setFeaturedStatus(String featuredStatus) { this.featuredStatus = featuredStatus; }

    public Boolean getIsGoldFeatured() { return isGoldFeatured; }
    public void setIsGoldFeatured(Boolean isGoldFeatured) { this.isGoldFeatured = isGoldFeatured; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getOpenedAt() { return openedAt; }
    public void setOpenedAt(LocalDateTime openedAt) { this.openedAt = openedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getFeaturedApplyAt() { return featuredApplyAt; }
    public void setFeaturedApplyAt(LocalDateTime featuredApplyAt) { this.featuredApplyAt = featuredApplyAt; }

    public String getFeaturedApplyReason() { return featuredApplyReason; }
    public void setFeaturedApplyReason(String featuredApplyReason) { this.featuredApplyReason = featuredApplyReason; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getPendingEnrollCount() { return pendingEnrollCount; }
    public void setPendingEnrollCount(Integer pendingEnrollCount) { this.pendingEnrollCount = pendingEnrollCount; }

    public Integer getCourseCount() { return courseCount; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Integer getTotalEnrollments() { return totalEnrollments; }
    public void setTotalEnrollments(Integer totalEnrollments) { this.totalEnrollments = totalEnrollments; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }
}

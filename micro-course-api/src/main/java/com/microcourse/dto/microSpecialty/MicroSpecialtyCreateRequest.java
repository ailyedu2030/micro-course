package com.microcourse.dto.microSpecialty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class MicroSpecialtyCreateRequest {

    @NotBlank(message = "微专业代码不能为空")
    private String code;

    @NotBlank(message = "微专业名称不能为空")
    private String title;

    @NotNull(message = "开课学院不能为空")
    private Long offerDepartmentId;

    @NotNull(message = "负责人不能为空")
    private Long leadTeacherId;

    private String subtitle;
    private String coverUrl;
    private String description;
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
    private String semester;

    public MicroSpecialtyCreateRequest() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }

    public Long getLeadTeacherId() { return leadTeacherId; }
    public void setLeadTeacherId(Long leadTeacherId) { this.leadTeacherId = leadTeacherId; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
}

package com.microcourse.dto.microSpecialty;
import java.math.BigDecimal;
public class MicroSpecialtyUpdateRequest {
    private String title; private String subtitle; private String coverUrl; private String description;
    private Long offerDepartmentId; private String targetAudience; private String trainingObjective;
    private String admissionRequirement; private String completionRule; private BigDecimal totalCredits;
    private Integer totalHours; private BigDecimal minCredits; private Integer maxStudents;
    private String semester; private Integer version;
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getSubtitle() { return subtitle; } public void setSubtitle(String v) { subtitle = v; }
    public String getCoverUrl() { return coverUrl; } public void setCoverUrl(String v) { coverUrl = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public Long getOfferDepartmentId() { return offerDepartmentId; } public void setOfferDepartmentId(Long v) { offerDepartmentId = v; }
    public String getTargetAudience() { return targetAudience; } public void setTargetAudience(String v) { targetAudience = v; }
    public String getTrainingObjective() { return trainingObjective; } public void setTrainingObjective(String v) { trainingObjective = v; }
    public String getAdmissionRequirement() { return admissionRequirement; } public void setAdmissionRequirement(String v) { admissionRequirement = v; }
    public String getCompletionRule() { return completionRule; } public void setCompletionRule(String v) { completionRule = v; }
    public BigDecimal getTotalCredits() { return totalCredits; } public void setTotalCredits(BigDecimal v) { totalCredits = v; }
    public Integer getTotalHours() { return totalHours; } public void setTotalHours(Integer v) { totalHours = v; }
    public BigDecimal getMinCredits() { return minCredits; } public void setMinCredits(BigDecimal v) { minCredits = v; }
    public Integer getMaxStudents() { return maxStudents; } public void setMaxStudents(Integer v) { maxStudents = v; }
    public String getSemester() { return semester; } public void setSemester(String v) { semester = v; }
    public Integer getVersion() { return version; } public void setVersion(Integer v) { version = v; }
}

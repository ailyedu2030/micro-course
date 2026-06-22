package com.microcourse.dto.microSpecialty;
import jakarta.validation.constraints.*;
public class MicroSpecialtyProposalRequest {
    @NotBlank(message = "申报标题不能为空") private String title;
    private String description;
    @NotNull(message = "开课学院不能为空") private Long offerDepartmentId;
    private String trainingObjective; private String semester; private Integer maxStudents;
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public Long getOfferDepartmentId() { return offerDepartmentId; } public void setOfferDepartmentId(Long v) { offerDepartmentId = v; }
    public String getTrainingObjective() { return trainingObjective; } public void setTrainingObjective(String v) { trainingObjective = v; }
    public String getSemester() { return semester; } public void setSemester(String v) { semester = v; }
    public Integer getMaxStudents() { return maxStudents; } public void setMaxStudents(Integer v) { maxStudents = v; }
}

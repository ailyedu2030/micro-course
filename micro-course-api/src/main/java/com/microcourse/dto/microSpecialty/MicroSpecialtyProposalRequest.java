package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class MicroSpecialtyProposalRequest {

    @NotBlank(message = "微专业名称不能为空")
    private String title;

    // BUG-003 fix: 前端传 offerDepartmentIds(复数), 后端字段名对齐
    @NotNull(message = "开课学院不能为空")
    @JsonProperty("offerDepartmentIds")
    private Long offerDepartmentId;

    private String description;
    private String trainingObjective;
    private String prerequisites;
    private String semester;
    private Integer maxStudents;
    /** 总学分（兼容旧字段 credits，同时支持新字段 totalCredits） */
    private BigDecimal credits;
    /** 总学分（新字段，Phase 15 数据字典 §12.1）。与 credits 同义但类型为 Integer */
    private Integer totalCredits;

    public MicroSpecialtyProposalRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTrainingObjective() { return trainingObjective; }
    public void setTrainingObjective(String trainingObjective) { this.trainingObjective = trainingObjective; }

    public String getPrerequisites() { return prerequisites; }
    public void setPrerequisites(String prerequisites) { this.prerequisites = prerequisites; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }

    public Integer getTotalCredits() { return totalCredits; }
    public void setTotalCredits(Integer totalCredits) { this.totalCredits = totalCredits; }
}

package com.microcourse.dto.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public class ProposalCourseItem {

    private Long id;
    private String moduleName;
    @NotBlank(message = "课程名称不能为空")
    private String courseName;
    @DecimalMin(value = "0", message = "学时不能为负数")
    private Integer hours;
    @DecimalMin(value = "0.0", message = "学分不能为负数")
    @DecimalMax(value = "99.9", message = "学分超出合理范围")
    private BigDecimal credits;
    private String semester;

    // Phase 1: 课程下的章节列表
    @Valid
    private List<ProposalChapterItem> chapters;

    public ProposalCourseItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public List<ProposalChapterItem> getChapters() { return chapters; }
    public void setChapters(List<ProposalChapterItem> chapters) { this.chapters = chapters; }
}

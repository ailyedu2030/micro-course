package com.microcourse.dto.storage;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public class ProposalLeadCourseItem {

    private Long id;
    @NotBlank(message = "课程名称不能为空")
    private String courseName;
    @DecimalMin(value = "0.0", message = "学分不能为负数")
    @DecimalMax(value = "99.9", message = "学分超出合理范围")
    private BigDecimal credits;
    @DecimalMin(value = "0", message = "学时不能为负数")
    private Integer hours;

    public ProposalLeadCourseItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
}

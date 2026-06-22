package com.microcourse.dto.microSpecialty;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MicroSpecialtyFeaturedVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String departmentName;
    private String leadTeacherName;
    private BigDecimal totalCredits;
    private Integer courseCount;
    private Integer teacherCount;
    private Integer studentCount;
    private String status;
    private Boolean isGoldFeatured;
    private BigDecimal qualityScore;

    public MicroSpecialtyFeaturedVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public String getLeadTeacherName() { return leadTeacherName; }
    public void setLeadTeacherName(String leadTeacherName) { this.leadTeacherName = leadTeacherName; }

    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }

    public Integer getCourseCount() { return courseCount; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }

    public Integer getTeacherCount() { return teacherCount; }
    public void setTeacherCount(Integer teacherCount) { this.teacherCount = teacherCount; }

    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getIsGoldFeatured() { return isGoldFeatured; }
    public void setIsGoldFeatured(Boolean isGoldFeatured) { this.isGoldFeatured = isGoldFeatured; }

    public BigDecimal getQualityScore() { return qualityScore; }
    public void setQualityScore(BigDecimal qualityScore) { this.qualityScore = qualityScore; }
}

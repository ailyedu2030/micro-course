package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CourseCreateRequest {

    @NotBlank(message = "课程标题不能为空")
    private String title;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    @NotNull(message = "教师ID不能为空")
    private Long teacherId;

    private String subtitle;

    private String summary;

    private String coverUrl;

    private Long offerDepartmentId;

    private String semester;

    private BigDecimal creditHours;

    private String courseNature;

    private Integer maxStudents;

    private Integer difficulty;

    private String description;

    private String tags;

    private String courseType;

    private java.math.BigDecimal price;

    public CourseCreateRequest() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public BigDecimal getCreditHours() { return creditHours; }
    public void setCreditHours(BigDecimal creditHours) { this.creditHours = creditHours; }
    public String getCourseNature() { return courseNature; }
    public void setCourseNature(String courseNature) { this.courseNature = courseNature; }
    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
}
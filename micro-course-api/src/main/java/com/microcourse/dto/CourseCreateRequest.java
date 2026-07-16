package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class CourseCreateRequest {

    @NotBlank(message = "课程标题不能为空")
    private String title;

    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    private Long teacherId;  // 非空由Service层根据当前用户填充,Controller层@Valid不校验

    private String subtitle;

    @Size(max = 300, message = "课程简介不能超过300字")
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

    private Boolean isFree;

    private String freeAccessScope;
    private String freeDeptIds;
    private String discountScope;
    private Integer discountPercent;

    // ===== P1 Stage 1: 课程级元信息(Trae SKILL.md 模块 3.1 schema)=====
    @Size(max = 64, message = "hid 全局唯一 ID 长度不超过 64")
    private String hid;

    private Integer totalHours;

    private Integer totalWeeks;

    private List<String> teachingPhilosophy;

    @Pattern(regexp = "online-self-study|offline-blended|hybrid", message = "learningMode 必须是 online-self-study / offline-blended / hybrid")
    private String learningMode;

    private String evaluationScheme;

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

    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }

    public String getFreeAccessScope() { return freeAccessScope; }
    public void setFreeAccessScope(String s) { this.freeAccessScope = s; }
    public String getFreeDeptIds() { return freeDeptIds; }
    public void setFreeDeptIds(String s) { this.freeDeptIds = s; }
    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String s) { this.discountScope = s; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer n) { this.discountPercent = n; }

    // ===== P1 getters/setters =====
    public String getHid() { return hid; }
    public void setHid(String hid) { this.hid = hid; }
    public Integer getTotalHours() { return totalHours; }
    public void setTotalHours(Integer totalHours) { this.totalHours = totalHours; }
    public Integer getTotalWeeks() { return totalWeeks; }
    public void setTotalWeeks(Integer totalWeeks) { this.totalWeeks = totalWeeks; }
    public List<String> getTeachingPhilosophy() { return teachingPhilosophy; }
    public void setTeachingPhilosophy(List<String> teachingPhilosophy) { this.teachingPhilosophy = teachingPhilosophy; }
    public String getLearningMode() { return learningMode; }
    public void setLearningMode(String learningMode) { this.learningMode = learningMode; }
    public String getEvaluationScheme() { return evaluationScheme; }
    public void setEvaluationScheme(String evaluationScheme) { this.evaluationScheme = evaluationScheme; }
}
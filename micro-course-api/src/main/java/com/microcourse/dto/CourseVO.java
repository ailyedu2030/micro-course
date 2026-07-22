package com.microcourse.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseVO {

    private Long id;
    private String title;
    private String subtitle;
    private String summary;
    private String coverUrl;
    private Long categoryId;
    private String categoryName;
    private Long teacherId;
    private String teacherName;
    private Long offerDepartmentId;
    private String semester;
    private BigDecimal creditHours;
    private String courseNature;
    private Integer maxStudents;
    private Integer difficulty;
    private Integer status;
    private String statusText;
    private String rejectReason;
    private String description;
    private Integer studentCount;
    private BigDecimal avgRating;
    private Integer ratingCount;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private Boolean isRecommended;
    private String tags;
    private List<ChapterVO> chapters;
    private String courseType;
    private java.math.BigDecimal price;
    private Boolean isFree;
    private java.math.BigDecimal listPrice;
    private String freeAccessScope;
    private String freeAccessScopeLabel;
    private String discountScope;
    private Integer discountPercent;
    private String pricingStatus;
    /** 复制课程时标记：视频未随课程复制，需手动上传 */
    private Boolean videoCopied;

    public CourseVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
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
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }
    public Integer getRatingCount() { return ratingCount; }
    public void setRatingCount(Integer ratingCount) { this.ratingCount = ratingCount; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Boolean getIsRecommended() { return isRecommended; }
    public void setIsRecommended(Boolean isRecommended) { this.isRecommended = isRecommended; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public List<ChapterVO> getChapters() { return chapters; }
    public void setChapters(List<ChapterVO> chapters) { this.chapters = chapters; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
    public java.math.BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(java.math.BigDecimal listPrice) { this.listPrice = listPrice; }
    public String getFreeAccessScope() { return freeAccessScope; }
    public void setFreeAccessScope(String freeAccessScope) { this.freeAccessScope = freeAccessScope; }
    public String getFreeAccessScopeLabel() { return freeAccessScopeLabel; }
    public void setFreeAccessScopeLabel(String freeAccessScopeLabel) { this.freeAccessScopeLabel = freeAccessScopeLabel; }
    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String discountScope) { this.discountScope = discountScope; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public String getPricingStatus() { return pricingStatus; }
    public void setPricingStatus(String pricingStatus) { this.pricingStatus = pricingStatus; }
    public Boolean getVideoCopied() { return videoCopied; }
    public void setVideoCopied(Boolean videoCopied) { this.videoCopied = videoCopied; }

    // ===== P1 Stage 1: 课程级元信息(交叉审查 P1-1:VO 必须包含新字段) =====
    private String hid;
    private Integer totalHours;
    private Integer totalWeeks;
    private List<String> teachingPhilosophy;
    private String learningMode;
    private String evaluationScheme;

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

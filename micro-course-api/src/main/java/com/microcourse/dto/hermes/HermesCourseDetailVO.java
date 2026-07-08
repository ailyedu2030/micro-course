package com.microcourse.dto.hermes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class HermesCourseDetailVO {

    private String hermesCourseId;
    private Long courseId;
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
    private String description;
    private String tags;
    private String courseType;
    private String rejectReason;
    private Integer studentCount;
    private BigDecimal avgRating;
    private LocalDateTime publishedAt;
    private LocalDateTime lastSyncAt;
    private LocalDateTime createdAt;

    private PricingVo pricing;
    private List<ChapterVo> chapters;

    public HermesCourseDetailVO() {}

    public String getHermesCourseId() { return hermesCourseId; }
    public void setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
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
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public PricingVo getPricing() { return pricing; }
    public void setPricing(PricingVo pricing) { this.pricing = pricing; }
    public List<ChapterVo> getChapters() { return chapters; }
    public void setChapters(List<ChapterVo> chapters) { this.chapters = chapters; }

    public static class PricingVo {
        private Boolean isFree;
        private BigDecimal price;
        private String freeAccessScope;
        private String freeDeptIds;

        public Boolean getIsFree() { return isFree; }
        public void setIsFree(Boolean isFree) { this.isFree = isFree; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public String getFreeAccessScope() { return freeAccessScope; }
        public void setFreeAccessScope(String freeAccessScope) { this.freeAccessScope = freeAccessScope; }
        public String getFreeDeptIds() { return freeDeptIds; }
        public void setFreeDeptIds(String freeDeptIds) { this.freeDeptIds = freeDeptIds; }
    }

    public static class ChapterVo {
        private Long id;
        private String title;
        private Integer sortOrder;
        private List<LessonVo> lessons;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public List<LessonVo> getLessons() { return lessons; }
        public void setLessons(List<LessonVo> lessons) { this.lessons = lessons; }
    }

    public static class LessonVo {
        private Long id;
        private String title;
        private String lessonType;
        private String contentUrl;
        private Integer durationMinutes;
        private Integer sortOrder;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getLessonType() { return lessonType; }
        public void setLessonType(String lessonType) { this.lessonType = lessonType; }
        public String getContentUrl() { return contentUrl; }
        public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
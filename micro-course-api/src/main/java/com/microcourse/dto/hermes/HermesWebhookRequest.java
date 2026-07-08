package com.microcourse.dto.hermes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class HermesWebhookRequest {

    @NotBlank(message = "Hermes 课程 ID 不能为空")
    private String hermesCourseId;

    @NotBlank(message = "课程标题不能为空")
    private String title;

    @NotNull(message = "分类 ID 不能为空")
    private Long categoryId;

    /**
     * 教师 ID（可选）。
     * 如果提供，必须等于 API Key 对应的教师 ID；
     * 不提供则默认使用 API Key 反查得到的教师。
     */
    private Long teacherId;

    private String subtitle;

    @Size(max = 300, message = "课程简介不能超过 300 字")
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

    private List<ChapterDto> chapters;
    private PricingDto pricing;

    public HermesWebhookRequest() {}

    public String getHermesCourseId() { return hermesCourseId; }
    public void setHermesCourseId(String hermesCourseId) { this.hermesCourseId = hermesCourseId; }
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
    public List<ChapterDto> getChapters() { return chapters; }
    public void setChapters(List<ChapterDto> chapters) { this.chapters = chapters; }
    public PricingDto getPricing() { return pricing; }
    public void setPricing(PricingDto pricing) { this.pricing = pricing; }

    public static class ChapterDto {
        private String title;
        private Integer sortOrder;
        private List<LessonDto> lessons;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
        public List<LessonDto> getLessons() { return lessons; }
        public void setLessons(List<LessonDto> lessons) { this.lessons = lessons; }
    }

    public static class LessonDto {
        private String title;
        private String type;
        private String contentUrl;
        private Integer durationMinutes;
        private Integer sortOrder;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getContentUrl() { return contentUrl; }
        public void setContentUrl(String contentUrl) { this.contentUrl = contentUrl; }
        public Integer getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class PricingDto {
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
}

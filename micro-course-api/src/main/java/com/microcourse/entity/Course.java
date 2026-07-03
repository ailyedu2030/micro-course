package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("courses")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String subtitle;
    private String summary;
    private String coverUrl;
    @TableField("category_id")
    private Long categoryId;
    @TableField("teacher_id")
    private Long teacherId;
    @TableField("offer_department_id")
    private Long offerDepartmentId;
    private String semester;
    private BigDecimal creditHours;
    private String courseNature;
    private Integer maxStudents;
    private Integer difficulty;
    private Integer status;
    private String rejectReason;
    private String description;
    private Integer studentCount;
    private BigDecimal avgRating;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version
    private Integer version;

    @TableField("is_recommended")
    private Boolean isRecommended;

    @TableField("tags")  // V23 migration: ALTER TABLE courses ADD COLUMN tags TEXT
    private String tags;

    private String courseType;
    private BigDecimal price;

    @TableField("free_access_scope")
    private String freeAccessScope;  // none | same_department | same_college | same_school

    @TableField(value = "free_dept_ids", typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String freeDeptIds;     // JSON 字符串

    @TableField("discount_scope")
    private String discountScope;   // none | same_college | same_school

    @TableField("discount_percent")
    private Integer discountPercent; // 0-100

    @TableField("list_price")
    private BigDecimal listPrice; // 课程标价 (alias for price, 教学员看)

    @TableField("pricing_status")
    private String pricingStatus; // DRAFT | PENDING | APPROVED | REJECTED

    @TableField("pricing_reviewed_at")
    private LocalDateTime pricingReviewedAt;

    @TableField("pricing_reviewed_by")
    private Long pricingReviewedBy;

    private Boolean isFree;

    @TableLogic(value = "NULL", delval = "now()")
    private LocalDateTime deletedAt;

    public Course() {}

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
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
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
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public BigDecimal getAvgRating() { return avgRating; }
    public void setAvgRating(BigDecimal avgRating) { this.avgRating = avgRating; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Boolean getIsRecommended() { return isRecommended; }
    public void setIsRecommended(Boolean isRecommended) { this.isRecommended = isRecommended; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getFreeAccessScope() { return freeAccessScope; }
    public void setFreeAccessScope(String freeAccessScope) { this.freeAccessScope = freeAccessScope; }
    public String getFreeDeptIds() { return freeDeptIds; }
    public void setFreeDeptIds(String freeDeptIds) { this.freeDeptIds = freeDeptIds; }
    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String discountScope) { this.discountScope = discountScope; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal listPrice) { this.listPrice = listPrice; }
    public String getPricingStatus() { return pricingStatus; }
    public void setPricingStatus(String pricingStatus) { this.pricingStatus = pricingStatus; }
    public LocalDateTime getPricingReviewedAt() { return pricingReviewedAt; }
    public void setPricingReviewedAt(LocalDateTime pricingReviewedAt) { this.pricingReviewedAt = pricingReviewedAt; }
    public Long getPricingReviewedBy() { return pricingReviewedBy; }
    public void setPricingReviewedBy(Long pricingReviewedBy) { this.pricingReviewedBy = pricingReviewedBy; }
}
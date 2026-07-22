package com.microcourse.dto;

import java.util.Set;

public class CoursePageQuery {

    /** 有效排序字段白名单：studentCount / avgRating / updatedAt */
    public static final Set<String> VALID_SORT_BY = Set.of("studentCount", "avgRating", "updatedAt");
    /** 有效排序方向白名单：asc / desc */
    public static final Set<String> VALID_SORT_ORDER = Set.of("asc", "desc");

    private String title;
    private String keyword;
    private Long categoryId;
    private Long teacherId;
    private Integer status;
    private Boolean recommended;
    private Integer difficulty;
    private int page;
    private int size;
    private String sortBy;
    private String sortOrder;
    private String courseType;
    private String teacherName;
    private Long offerDepartmentId;

    public CoursePageQuery() {}

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getRecommended() { return recommended; }
    public void setRecommended(Boolean recommended) { this.recommended = recommended; }
    public Integer getDifficulty() { return difficulty; }
    public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    public String getCourseType() { return courseType; }
    public void setCourseType(String courseType) { this.courseType = courseType; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public Long getOfferDepartmentId() { return offerDepartmentId; }
    public void setOfferDepartmentId(Long offerDepartmentId) { this.offerDepartmentId = offerDepartmentId; }
}

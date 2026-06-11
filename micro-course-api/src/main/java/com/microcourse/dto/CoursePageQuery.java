package com.microcourse.dto;

public class CoursePageQuery {

    private String title;
    private String keyword;
    private Long categoryId;
    private Long teacherId;
    private Integer status;
    private int page;
    private int size;

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
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
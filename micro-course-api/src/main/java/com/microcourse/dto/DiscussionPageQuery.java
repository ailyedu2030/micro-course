package com.microcourse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class DiscussionPageQuery {

    private String keyword;
    private Long courseId;
    private String status;

    @Min(value = 0, message = "页码不能为负数")
    private int page;

    @Min(value = 1, message = "每页条数至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    private int size;

    public DiscussionPageQuery() {}

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
package com.microcourse.dto;

import java.math.BigDecimal;

public class TeacherCourseVO {

    private Long id;
    private String title;
    private String cover;
    private Integer studentCount;
    private BigDecimal rating;
    private Integer status;

    public TeacherCourseVO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
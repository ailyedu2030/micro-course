package com.microcourse.dto;

/**
 * 课程趋势 VO（按天聚合）
 */
public class CourseTrendVO {

    private String date;
    private Long newCourses;
    private Long enrollments;

    public CourseTrendVO() {}

    public CourseTrendVO(String date, Long newCourses, Long enrollments) {
        this.date = date;
        this.newCourses = newCourses;
        this.enrollments = enrollments;
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public Long getNewCourses() { return newCourses; }
    public void setNewCourses(Long newCourses) { this.newCourses = newCourses; }
    public Long getEnrollments() { return enrollments; }
    public void setEnrollments(Long enrollments) { this.enrollments = enrollments; }
}
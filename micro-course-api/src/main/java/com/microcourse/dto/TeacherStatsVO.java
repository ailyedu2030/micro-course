package com.microcourse.dto;

import java.time.LocalDateTime;

public class TeacherStatsVO {

    private Integer courseCount;
    private Integer studentCount;
    private Integer pendingHomework;
    private Integer pendingQuestions;

    public TeacherStatsVO() {}

    public Integer getCourseCount() { return courseCount; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public Integer getPendingHomework() { return pendingHomework; }
    public void setPendingHomework(Integer pendingHomework) { this.pendingHomework = pendingHomework; }
    public Integer getPendingQuestions() { return pendingQuestions; }
    public void setPendingQuestions(Integer pendingQuestions) { this.pendingQuestions = pendingQuestions; }
}
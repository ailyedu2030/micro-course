package com.microcourse.dto;

public class TeacherStatsVO {

    private Integer courseCount;
    private Integer studentCount;
    private Integer pendingHomework;
    private Integer pendingQuestions;
    private Double completionRate;
    private Double avgScore;

    public TeacherStatsVO() {}

    public Integer getCourseCount() { return courseCount; }
    public void setCourseCount(Integer courseCount) { this.courseCount = courseCount; }
    public Integer getStudentCount() { return studentCount; }
    public void setStudentCount(Integer studentCount) { this.studentCount = studentCount; }
    public Integer getPendingHomework() { return pendingHomework; }
    public void setPendingHomework(Integer pendingHomework) { this.pendingHomework = pendingHomework; }
    public Integer getPendingQuestions() { return pendingQuestions; }
    public void setPendingQuestions(Integer pendingQuestions) { this.pendingQuestions = pendingQuestions; }
    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
    public Double getAvgScore() { return avgScore; }
    public void setAvgScore(Double avgScore) { this.avgScore = avgScore; }
}

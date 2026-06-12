package com.microcourse.dto;

/**
 * 教务处驾驶舱 - 全校概览 VO
 */
public class AcademicOverviewVO {

    private Long totalCourses;
    private Long totalStudents;
    private Long totalEnrollments;
    private Double avgCompletionRate;
    private Double avgAccuracyRate;
    private String currentSemester;
    private Long updateAt;

    public AcademicOverviewVO() {}

    public Long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Long totalCourses) { this.totalCourses = totalCourses; }
    public Long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Long totalStudents) { this.totalStudents = totalStudents; }
    public Long getTotalEnrollments() { return totalEnrollments; }
    public void setTotalEnrollments(Long totalEnrollments) { this.totalEnrollments = totalEnrollments; }
    public Double getAvgCompletionRate() { return avgCompletionRate; }
    public void setAvgCompletionRate(Double avgCompletionRate) { this.avgCompletionRate = avgCompletionRate; }
    public Double getAvgAccuracyRate() { return avgAccuracyRate; }
    public void setAvgAccuracyRate(Double avgAccuracyRate) { this.avgAccuracyRate = avgAccuracyRate; }
    public String getCurrentSemester() { return currentSemester; }
    public void setCurrentSemester(String currentSemester) { this.currentSemester = currentSemester; }
    public Long getUpdateAt() { return updateAt; }
    public void setUpdateAt(Long updateAt) { this.updateAt = updateAt; }
}

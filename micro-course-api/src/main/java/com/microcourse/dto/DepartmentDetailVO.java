package com.microcourse.dto;

import java.util.List;

/**
 * 教务处驾驶舱 - 院系详情 VO
 */
public class DepartmentDetailVO {

    private Long departmentId;
    private String departmentName;
    private List<CourseStatsVO> courses;
    private Long totalCourses;
    private Long totalEnrollments;
    private Double avgCompletionRate;
    private Double avgAccuracyRate;

    public DepartmentDetailVO() {}

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public List<CourseStatsVO> getCourses() { return courses; }
    public void setCourses(List<CourseStatsVO> courses) { this.courses = courses; }
    public Long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Long totalCourses) { this.totalCourses = totalCourses; }
    public Long getTotalEnrollments() { return totalEnrollments; }
    public void setTotalEnrollments(Long totalEnrollments) { this.totalEnrollments = totalEnrollments; }
    public Double getAvgCompletionRate() { return avgCompletionRate; }
    public void setAvgCompletionRate(Double avgCompletionRate) { this.avgCompletionRate = avgCompletionRate; }
    public Double getAvgAccuracyRate() { return avgAccuracyRate; }
    public void setAvgAccuracyRate(Double avgAccuracyRate) { this.avgAccuracyRate = avgAccuracyRate; }
}

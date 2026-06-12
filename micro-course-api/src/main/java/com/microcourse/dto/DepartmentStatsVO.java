package com.microcourse.dto;

/**
 * 教务处驾驶舱 - 院系列表 VO（含统计）
 */
public class DepartmentStatsVO {

    private Long departmentId;
    private String departmentName;
    private Long courseCount;
    private Long enrollmentCount;
    private Long studentCount;
    private Double avgCompletionRate;
    private Double avgAccuracyRate;
    private Integer ranking;

    public DepartmentStatsVO() {}

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public Long getCourseCount() { return courseCount; }
    public void setCourseCount(Long courseCount) { this.courseCount = courseCount; }
    public Long getEnrollmentCount() { return enrollmentCount; }
    public void setEnrollmentCount(Long enrollmentCount) { this.enrollmentCount = enrollmentCount; }
    public Long getStudentCount() { return studentCount; }
    public void setStudentCount(Long studentCount) { this.studentCount = studentCount; }
    public Double getAvgCompletionRate() { return avgCompletionRate; }
    public void setAvgCompletionRate(Double avgCompletionRate) { this.avgCompletionRate = avgCompletionRate; }
    public Double getAvgAccuracyRate() { return avgAccuracyRate; }
    public void setAvgAccuracyRate(Double avgAccuracyRate) { this.avgAccuracyRate = avgAccuracyRate; }
    public Integer getRanking() { return ranking; }
    public void setRanking(Integer ranking) { this.ranking = ranking; }
}

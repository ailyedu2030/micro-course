package com.microcourse.dto;

/**
 * 教务处驾驶舱 - 完成率预警 VO
 */
public class CompletionWarningVO {

    private Long courseId;
    private String courseTitle;
    private String teacherName;
    private Long enrollmentCount;
    private Double completionRate;
    private String status; // "warning" 或 "critical"

    public CompletionWarningVO() {}

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public Long getEnrollmentCount() { return enrollmentCount; }
    public void setEnrollmentCount(Long enrollmentCount) { this.enrollmentCount = enrollmentCount; }
    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

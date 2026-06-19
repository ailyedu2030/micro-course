package com.microcourse.dto;

public class EnrollmentQueryRequest {
    private Integer page = 0;
    private Integer size = 10;
    private Long teacherId;
    private String studentName;
    private String courseName;
    private String status;
    /** P0-4: 班级名称过滤（服务端关联 classes 表） */
    private String className;
    /** P0-4: 专业名称过滤（服务端关联 majors 表） */
    private String majorName;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
}
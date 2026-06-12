package com.microcourse.dto;

import com.microcourse.enums.UserRole;

public class UserPageQuery {

    private String keyword;
    private UserRole role;
    private Integer status;
    private Integer teacherStatus;
    private Long departmentId;
    private Long majorId;
    private Long classId;
    private int page;
    private int size;

    public UserPageQuery() {}

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getTeacherStatus() { return teacherStatus; }
    public void setTeacherStatus(Integer teacherStatus) { this.teacherStatus = teacherStatus; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
package com.microcourse.dto;

import jakarta.validation.constraints.Email;

public class UserUpdateRequest {

    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private Long departmentId;

    private Long majorId;

    private Long classId;

    private String grade;

    public UserUpdateRequest() {}

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}
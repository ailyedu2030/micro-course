package com.microcourse.dto;

import jakarta.validation.constraints.Email;

public class UserUpdateRequest {

    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String gender;

    private String avatar;

    private Long departmentId;

    private Long majorId;

    private Long classId;

    private String grade;

    private String enrollmentYear;

    private String graduationYear;

    /** 学号（学生） */
    private String studentNo;

    /** 工号（教师/教务） */
    private String teacherNo;

    /** 政治面貌 */
    private String politicalStatus;

    /** 状态：1 启用 2 禁用 */
    private Integer status;

    public UserUpdateRequest() {}

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(String enrollmentYear) { this.enrollmentYear = enrollmentYear; }
    public String getGraduationYear() { return graduationYear; }
    public void setGraduationYear(String graduationYear) { this.graduationYear = graduationYear; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getTeacherNo() { return teacherNo; }
    public void setTeacherNo(String teacherNo) { this.teacherNo = teacherNo; }
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String politicalStatus) { this.politicalStatus = politicalStatus; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
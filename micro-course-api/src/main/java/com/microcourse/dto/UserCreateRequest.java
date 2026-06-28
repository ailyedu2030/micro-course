package com.microcourse.dto;

import com.microcourse.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度为6-100位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @NotNull(message = "角色不能为空")
    private UserRole role;

    private Long departmentId;

    private Long majorId;

    private Long classId;

    private String gender;

    /** 学号（学生） */
    private String studentNo;

    /** 工号（教师/教务） */
    private String teacherNo;

    /** 入学年份（学生） */
    private String enrollmentYear;

    /** 毕业年份（学生） */
    private String graduationYear;

    /** 年级（学生，如 2024/2025 自动派生） */
    private String grade;

    /** 政治面貌 */
    private String politicalStatus;

    /** 状态：1 启用 2 禁用 3 已删除 */
    private Integer status;

    public UserCreateRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getTeacherNo() { return teacherNo; }
    public void setTeacherNo(String teacherNo) { this.teacherNo = teacherNo; }
    public String getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(String enrollmentYear) { this.enrollmentYear = enrollmentYear; }
    public String getGraduationYear() { return graduationYear; }
    public void setGraduationYear(String graduationYear) { this.graduationYear = graduationYear; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String politicalStatus) { this.politicalStatus = politicalStatus; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
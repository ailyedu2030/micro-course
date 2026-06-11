package com.microcourse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.microcourse.enums.UserRole;

import java.time.LocalDateTime;

@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password;

    @TableField("real_name")
    private String realName;
    private String email;
    private String phone;
    private String gender;
    private String avatar;
    private UserRole role;

    @TableField("department_id")
    private Long departmentId;

    @TableField("major_id")
    private Long majorId;

    @TableField("class_id")
    private Long classId;
    private String grade;

    @TableField("enrollment_year")
    private String enrollmentYear;

    @TableField("graduation_year")
    private String graduationYear;

    @TableField("political_status")
    private String politicalStatus;

    @TableField("student_no")
    private String studentNo;

    @TableField("teacher_no")
    private String teacherNo;
    private Integer status;

    @TableField("cas_bound")
    private Boolean casBound;

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public User() {}

    public User(Long id, String username, String password, String realName, String email, String phone,
                String gender, String avatar, UserRole role, Long departmentId, Long majorId, Long classId,
                String grade, String enrollmentYear, String graduationYear, String politicalStatus,
                String studentNo, String teacherNo, Integer status, Boolean casBound,
                LocalDateTime lastLoginAt, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id; this.username = username; this.password = password; this.realName = realName;
        this.email = email; this.phone = phone; this.gender = gender; this.avatar = avatar; this.role = role;
        this.departmentId = departmentId; this.majorId = majorId; this.classId = classId; this.grade = grade;
        this.enrollmentYear = enrollmentYear; this.graduationYear = graduationYear;
        this.politicalStatus = politicalStatus; this.studentNo = studentNo; this.teacherNo = teacherNo;
        this.status = status; this.casBound = casBound; this.lastLoginAt = lastLoginAt;
        this.deletedAt = deletedAt; this.createdAt = createdAt; this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
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
    public String getPoliticalStatus() { return politicalStatus; }
    public void setPoliticalStatus(String politicalStatus) { this.politicalStatus = politicalStatus; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getTeacherNo() { return teacherNo; }
    public void setTeacherNo(String teacherNo) { this.teacherNo = teacherNo; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getCasBound() { return casBound; }
    public void setCasBound(Boolean casBound) { this.casBound = casBound; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
package com.microcourse.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.microcourse.enums.UserRole;

/**
 * 用户批量导入 Excel 行模型
 * 必须列：username, realName
 * 可选列：email, role, departmentId, classId, majorId
 */
public class UserBatchImportDTO {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("真实姓名")
    private String realName;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("角色")
    private String role;

    @ExcelProperty("院系ID")
    private Long departmentId;

    @ExcelProperty("专业ID")
    private Long majorId;

    @ExcelProperty("班级ID")
    private Long classId;

    public UserBatchImportDTO() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getMajorId() { return majorId; }
    public void setMajorId(Long majorId) { this.majorId = majorId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
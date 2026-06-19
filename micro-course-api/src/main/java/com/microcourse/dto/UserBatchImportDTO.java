package com.microcourse.dto;

import com.alibaba.excel.annotation.ExcelProperty;

/**
 * 用户批量导入 Excel 行模型
 * 必须列：username, realName
 * 可选列：password, role, departmentName, majorName, className
 * P0-1 修复：改为按名称导入 department/major/class，后端根据名称查找 ID
 */
public class UserBatchImportDTO {

    @ExcelProperty("username")
    private String username;

    @ExcelProperty("realName")
    private String realName;

    @ExcelProperty("password")
    private String password;

    @ExcelProperty("role")
    private String role;

    @ExcelProperty("departmentName")
    private String departmentName;

    @ExcelProperty("majorName")
    private String majorName;

    @ExcelProperty("className")
    private String className;

    public UserBatchImportDTO() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
}

package com.microcourse.dto;

import java.time.LocalDateTime;

/**
 * P0-2: 学员详情弹窗 VO
 * 包含 users + classes + majors 关联字段
 */
public class StudentDetailVO {

    private Long userId;
    private String username;     // 学号（users.username）
    private String realName;     // 姓名
    private String className;    // 班级名称
    private String majorName;    // 专业名称
    private String email;
    private String phone;

    public StudentDetailVO() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}

package com.microcourse.dto;

/**
 * 学生搜索轻量 VO（教师端"添加教学班学生"弹窗使用）。
 * 仅暴露最小必要字段，避免把管理端全量用户信息暴露给教师角色。
 */
public class StudentSearchVO {
    private Long id;
    private String realName;
    private String username;
    private String studentNo;
    private String avatar;
    private Integer status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}

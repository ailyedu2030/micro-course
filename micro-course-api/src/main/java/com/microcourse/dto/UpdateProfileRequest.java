package com.microcourse.dto;

import jakarta.validation.constraints.Email;

public class UpdateProfileRequest {

    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String gender;

    /**
     * 【P1-C 修复】avatar 字段 (补充契约)
     * API 契约-Phase1 声明该字段可更新, 但 DTO 缺失
     * 允许用户通过此端点更新头像 URL (实际头像通过 POST /api/auth/me/avatar 上传)
     */
    private String avatar;

    public UpdateProfileRequest() {}

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
}
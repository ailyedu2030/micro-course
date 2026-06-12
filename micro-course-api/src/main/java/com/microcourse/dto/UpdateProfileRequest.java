package com.microcourse.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String gender;

    @Size(max = 500000, message = "头像数据过大，请使用小于 500KB 的图片")
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
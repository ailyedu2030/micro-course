package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置用户密码请求（A1.7 忘记密码兜底链路：登录页引导联系管理员重置）。
 */
public class ResetPasswordRequest {
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    public ResetPasswordRequest() {}
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}

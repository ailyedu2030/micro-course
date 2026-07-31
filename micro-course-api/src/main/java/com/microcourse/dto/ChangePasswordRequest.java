package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * R3 防御深度：DTO 层 + Service 层双层校验。
     * Service 层 {@code AuthServiceImpl.changePassword} 已有同样的正则（双层保险），
     * DTO 加 @Pattern 让 MethodArgumentNotValidException 在 controller 入口就拒绝。
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "新密码长度必须在8-32位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$",
            message = "新密码需 8-32 位且同时包含字母和数字")
    private String newPassword;

    public ChangePasswordRequest() {}

    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
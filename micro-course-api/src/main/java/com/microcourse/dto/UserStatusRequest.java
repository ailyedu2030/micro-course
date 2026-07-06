package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class UserStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;

    /**
     * 【P1-C 修复】INACTIVE→ACTIVE 激活守卫标志
     * 仅管理员强制激活时为 true, 跳过邮箱验证/CAS 绑定要求
     * 默认 false
     */
    private Boolean adminForceActivate = false;

    public UserStatusRequest() {}

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Boolean getAdminForceActivate() { return adminForceActivate; }
    public void setAdminForceActivate(Boolean adminForceActivate) {
        this.adminForceActivate = adminForceActivate;
    }
}
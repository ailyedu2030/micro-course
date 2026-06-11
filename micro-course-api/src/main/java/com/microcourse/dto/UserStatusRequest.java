package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class UserStatusRequest {

    @NotNull(message = "状态不能为空")
    private Integer status;

    public UserStatusRequest() {}

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
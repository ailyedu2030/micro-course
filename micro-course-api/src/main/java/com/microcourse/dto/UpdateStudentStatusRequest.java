package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateStudentStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    public UpdateStudentStatusRequest() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

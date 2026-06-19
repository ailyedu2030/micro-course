package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class AddStudentRequest {

    @NotNull(message = "学生ID不能为空")
    private Long userId;

    public AddStudentRequest() {
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}

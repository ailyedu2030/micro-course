package com.microcourse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CartAddRequest {
    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @Min(value = 1, message = "数量至少为1")
    private Integer quantity = 1;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}

package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateTaskRequest {
    @NotNull(message = "关联 slide 序号不能为空")
    @Min(value = 1, message = "slide 序号必须 ≥ 1")
    private Integer slide;

    @NotBlank(message = "任务描述不能为空")
    @Size(max = 2000, message = "任务描述不能超过 2000 字")
    private String description;

    public Integer getSlide() { return slide; }
    public void setSlide(Integer slide) { this.slide = slide; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

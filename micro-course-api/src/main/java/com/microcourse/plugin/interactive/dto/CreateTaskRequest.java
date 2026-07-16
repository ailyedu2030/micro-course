package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateTaskRequest {
    @NotNull @Min(1) private Integer slide;
    @NotBlank @Size(max = 2000) private String description;

    public Integer getSlide() { return slide; }
    public void setSlide(Integer slide) { this.slide = slide; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

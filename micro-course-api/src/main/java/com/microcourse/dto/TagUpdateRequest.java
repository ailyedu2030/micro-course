package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class TagUpdateRequest {

    @NotBlank(message = "标签名称不能为空")
    private String name;

    private String color;

    public TagUpdateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
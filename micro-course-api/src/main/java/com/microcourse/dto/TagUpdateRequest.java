package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class TagUpdateRequest {

    @NotBlank(message = "标签名称不能为空")
    private String name;

    public TagUpdateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
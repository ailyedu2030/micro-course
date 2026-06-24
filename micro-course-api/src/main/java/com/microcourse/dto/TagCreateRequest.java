package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class TagCreateRequest {

    @NotBlank
    private String name;

    private String color;

    public TagCreateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
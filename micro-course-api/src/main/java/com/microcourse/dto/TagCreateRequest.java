package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class TagCreateRequest {

    @NotBlank
    private String name;

    public TagCreateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
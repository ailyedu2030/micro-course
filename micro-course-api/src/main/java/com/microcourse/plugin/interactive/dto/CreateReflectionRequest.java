package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateReflectionRequest {
    @NotBlank @Size(max = 2000) private String template;

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
}

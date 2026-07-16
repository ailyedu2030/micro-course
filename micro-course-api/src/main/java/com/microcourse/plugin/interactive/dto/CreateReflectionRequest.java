package com.microcourse.plugin.interactive.dto;

import jakarta.validation.constraints.*;

public class CreateReflectionRequest {
    @NotBlank(message = "反思日志模板不能为空")
    @Size(max = 2000, message = "模板不能超过 2000 字")
    private String template;

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
}

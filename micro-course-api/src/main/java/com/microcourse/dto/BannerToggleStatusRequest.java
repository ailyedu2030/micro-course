package com.microcourse.dto;

import jakarta.validation.constraints.NotNull;

public class BannerToggleStatusRequest {

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    public BannerToggleStatusRequest() {}

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}

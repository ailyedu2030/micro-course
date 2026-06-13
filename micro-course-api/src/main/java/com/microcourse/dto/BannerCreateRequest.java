package com.microcourse.dto;

import jakarta.validation.constraints.NotBlank;

public class BannerCreateRequest {

    @NotBlank(message = "跳转链接不能为空")
    private String linkUrl;

    private Integer sortOrder = 0;

    private Boolean enabled = true;

    public BannerCreateRequest() {}

    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
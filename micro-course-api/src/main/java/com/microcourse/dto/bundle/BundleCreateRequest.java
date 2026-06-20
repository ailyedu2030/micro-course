package com.microcourse.dto.bundle;

import java.math.BigDecimal;

public class BundleCreateRequest {
    private String title;
    private String description;
    private String coverUrl;
    private Long creatorId;
    private BigDecimal price;
    private Boolean isFree;

    public BundleCreateRequest() {}
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
}

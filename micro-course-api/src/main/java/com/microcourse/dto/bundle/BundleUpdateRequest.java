package com.microcourse.dto.bundle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class BundleUpdateRequest {
    @NotBlank(message = "套餐名称不能为空")
    @Size(max = 200, message = "套餐名称不能超过 200 字符")
    private String title;

    @Size(max = 5000, message = "套餐描述不能超过 5000 字符")
    private String description;

    @Size(max = 500, message = "封面 URL 不能超过 500 字符")
    private String coverUrl;

    @DecimalMin(value = "0.00", message = "价格不能为负数")
    private BigDecimal price;
    private Boolean isFree;

    public BundleUpdateRequest() {}
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
}

package com.microcourse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class CoursePricingRequest {
    @NotNull @PositiveOrZero
    private BigDecimal basePrice;

    @Pattern(regexp = "^(none|same_department|same_college|same_school)?$")
    private String freeAccessScope;

    private String freeDeptIds;

    @Min(0) @Max(100)
    private Integer discountPercent;

    @Pattern(regexp = "^(none|same_college|same_school)?$")
    private String discountScope;

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }
    public String getFreeAccessScope() { return freeAccessScope; }
    public void setFreeAccessScope(String freeAccessScope) { this.freeAccessScope = freeAccessScope; }
    public String getFreeDeptIds() { return freeDeptIds; }
    public void setFreeDeptIds(String freeDeptIds) { this.freeDeptIds = freeDeptIds; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String discountScope) { this.discountScope = discountScope; }
}

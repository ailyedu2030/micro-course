package com.microcourse.dto;

import java.math.BigDecimal;

public class PricingForAdopterVO {

    private BigDecimal originalPrice;
    private BigDecimal adjustedPrice;
    private String pricingMessage;
    private String discountScope;
    private Integer discountPercent;
    private Boolean isFree;

    public PricingForAdopterVO() {}

    public BigDecimal getOriginalPrice() { return originalPrice; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public BigDecimal getAdjustedPrice() { return adjustedPrice; }
    public void setAdjustedPrice(BigDecimal adjustedPrice) { this.adjustedPrice = adjustedPrice; }
    public String getPricingMessage() { return pricingMessage; }
    public void setPricingMessage(String pricingMessage) { this.pricingMessage = pricingMessage; }
    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String discountScope) { this.discountScope = discountScope; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public Boolean getIsFree() { return isFree; }
    public void setIsFree(Boolean isFree) { this.isFree = isFree; }
}

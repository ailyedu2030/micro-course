package com.microcourse.dto;

import java.math.BigDecimal;

/**
 * 课程定价信息（学生端可见）
 * 展示标价、用户的实际价格、免费/折扣说明
 */
public class CoursePricingInfoVO {

    private BigDecimal listPrice;
    private BigDecimal finalPrice;
    private boolean free;
    private String freeAccessScope;
    private String freeAccessScopeLabel;
    private String discountScope;
    private Integer discountPercent;
    private String feeNote;

    public CoursePricingInfoVO() {}

    public BigDecimal getListPrice() { return listPrice; }
    public void setListPrice(BigDecimal listPrice) { this.listPrice = listPrice; }

    public BigDecimal getFinalPrice() { return finalPrice; }
    public void setFinalPrice(BigDecimal finalPrice) { this.finalPrice = finalPrice; }

    public boolean isFree() { return free; }
    public void setFree(boolean free) { this.free = free; }

    public String getFreeAccessScope() { return freeAccessScope; }
    public void setFreeAccessScope(String freeAccessScope) { this.freeAccessScope = freeAccessScope; }

    public String getFreeAccessScopeLabel() { return freeAccessScopeLabel; }
    public void setFreeAccessScopeLabel(String freeAccessScopeLabel) { this.freeAccessScopeLabel = freeAccessScopeLabel; }

    public String getDiscountScope() { return discountScope; }
    public void setDiscountScope(String discountScope) { this.discountScope = discountScope; }

    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }

    public String getFeeNote() { return feeNote; }
    public void setFeeNote(String feeNote) { this.feeNote = feeNote; }
}

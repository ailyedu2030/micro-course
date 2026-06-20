package com.microcourse.dto.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class OrderVO {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long courseId;
    private String courseTitle;
    private Long bundleId;
    private BigDecimal amount;
    private String status;
    private String statusText;
    private String paymentMethod;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public OrderVO() {}
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public Long getBundleId() { return bundleId; }
    public void setBundleId(Long bundleId) { this.bundleId = bundleId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public static String statusText(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "PENDING": return "待支付";
            case "PAID": return "已支付";
            case "CANCELLED": return "已取消";
            case "REFUNDED": return "已退款";
            default: return status;
        }
    }
}

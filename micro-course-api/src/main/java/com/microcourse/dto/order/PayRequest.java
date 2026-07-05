package com.microcourse.dto.order;

/**
 * 支付请求 DTO
 * 替代原始 Map<String, String> 请求体
 */
public class PayRequest {

    private String paymentMethod;

    public PayRequest() {}

    public String getPaymentMethod() { return paymentMethod; }

    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}

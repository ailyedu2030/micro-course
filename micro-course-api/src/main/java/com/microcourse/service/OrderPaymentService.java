package com.microcourse.service;

import com.microcourse.dto.order.OrderVO;

import java.util.Map;

public interface OrderPaymentService {

    /**
     * 创建订单（付费 → PENDING，免费 → PAID + 自动选课）
     */
    OrderVO createOrder(Long userId, Long courseId, Long bundleId);

    /**
     * 发起支付（PENDING → PAID + 选课 + Payment 流水）
     */
    OrderVO pay(Long orderId, String paymentMethod);

    /**
     * 支付网关回调（外部调用，含 HMAC 签名验证）
     */
    void paymentCallback(Map<String, String> params);
}

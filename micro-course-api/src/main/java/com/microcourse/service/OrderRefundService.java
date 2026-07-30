package com.microcourse.service;

import com.microcourse.dto.order.OrderVO;

import java.util.List;

public interface OrderRefundService {

    /**
     * 取消订单（PENDING → CANCELLED）
     */
    OrderVO cancelOrder(Long orderId);

    /**
     * 退款（PAID → REFUNDED，含分布式锁 + 学习进度检查）
     */
    OrderVO refund(Long orderId);

    /**
     * 批量下单 + 支付（创建失败全回滚，支付失败不阻断）
     */
    List<OrderVO> batchCreate(Long userId, List<Long> courseIds, String paymentMethod);
}

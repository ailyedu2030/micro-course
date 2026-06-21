package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;

import java.util.Map;

public interface OrderService {

    OrderVO createOrder(Long userId, Long courseId, Long bundleId);

    OrderVO getOrder(Long orderId);

    PageResult<OrderVO> getMyOrders(Long userId, int page, int size);

    OrderVO pay(Long orderId, String paymentMethod);

    OrderVO cancelOrder(Long orderId);

    void paymentCallback(Map<String, String> params);
}

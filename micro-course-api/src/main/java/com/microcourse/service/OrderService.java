package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;

import java.util.List;
import java.util.Map;

public interface OrderService {

    OrderVO createOrder(Long userId, Long courseId, Long bundleId);

    OrderVO getOrder(Long orderId);

    PageResult<OrderVO> getMyOrders(Long userId, int page, int size);

    OrderVO pay(Long orderId, String paymentMethod);

    OrderVO cancelOrder(Long orderId);

    void paymentCallback(Map<String, String> params);

    /**
     * J9-02: 退款（将 PAID 订单转为 REFUNDED，记录退款 Payment）
     */
    OrderVO refund(Long orderId);

    /**
     * 批量下单：事务原子性创建多个订单并支付。
     * 创建过程中任一失败则全部回滚；支付失败不阻断其他课程（订单保持 PENDING）。
     * @param userId 用户ID
     * @param courseIds 课程ID列表
     * @param paymentMethod 支付方式
     * @return 订单列表
     */
    List<OrderVO> batchCreate(Long userId, List<Long> courseIds, String paymentMethod);
}

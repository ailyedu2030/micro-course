package com.microcourse.service.impl;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.service.OrderPaymentService;
import com.microcourse.service.OrderQueryService;
import com.microcourse.service.OrderRefundService;
import com.microcourse.service.OrderService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 订单服务统一入口（Facade）。
 * <p>业务实现已按职责拆分到三个子 Service：</p>
 * <ul>
 *   <li>{@link OrderQueryServiceImpl} — 查询 + VO 转换 + 定时任务</li>
 *   <li>{@link OrderPaymentServiceImpl} — 创建订单 + 支付 + 回调</li>
 *   <li>{@link OrderRefundServiceImpl} — 取消 + 退款 + 批量下单</li>
 * </ul>
 * Controller 通过本 Facade 调用，无需修改注入点。
 */
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderQueryService orderQueryService;
    private final OrderPaymentService orderPaymentService;
    private final OrderRefundService orderRefundService;

    public OrderServiceImpl(OrderQueryService orderQueryService,
                            OrderPaymentService orderPaymentService,
                            OrderRefundService orderRefundService) {
        this.orderQueryService = orderQueryService;
        this.orderPaymentService = orderPaymentService;
        this.orderRefundService = orderRefundService;
    }

    @Override
    public OrderVO createOrder(Long userId, Long courseId, Long bundleId) {
        return orderPaymentService.createOrder(userId, courseId, bundleId);
    }

    @Override
    public OrderVO getOrder(Long orderId) {
        return orderQueryService.getOrder(orderId);
    }

    @Override
    public PageResult<OrderVO> getMyOrders(Long userId, int page, int size, Long courseId, String status) {
        return orderQueryService.getMyOrders(userId, page, size, courseId, status);
    }

    @Override
    public java.util.List<OrderVO> adminListOrders(Long teacherId) {
        return orderQueryService.adminListOrders(teacherId);
    }

    @Override
    public OrderVO pay(Long orderId, String paymentMethod) {
        return orderPaymentService.pay(orderId, paymentMethod);
    }

    @Override
    public OrderVO cancelOrder(Long orderId) {
        return orderRefundService.cancelOrder(orderId);
    }

    @Override
    public void paymentCallback(Map<String, String> params) {
        orderPaymentService.paymentCallback(params);
    }

    @Override
    public OrderVO refund(Long orderId) {
        return orderRefundService.refund(orderId);
    }

    @Override
    public List<OrderVO> batchCreate(Long userId, List<Long> courseIds, String paymentMethod) {
        return orderRefundService.batchCreate(userId, courseIds, paymentMethod);
    }
}

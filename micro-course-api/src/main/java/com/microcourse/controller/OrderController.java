package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.service.OrderService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT')")
    @AuditedLog("创建订单")
    public R<OrderVO> createOrder(@RequestBody Map<String, Long> body) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(orderService.createOrder(userId, body.get("courseId"), body.get("bundleId")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<OrderVO> getOrder(@PathVariable Long id) {
        return R.ok(orderService.getOrder(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('STUDENT')")
    public R<PageResult<OrderVO>> getMyOrders(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size) {
        return R.ok(orderService.getMyOrders(SecurityUtil.getCurrentUserId(), page, size));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAnyRole('STUDENT')")
    @AuditedLog("订单支付")
    public R<OrderVO> pay(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(orderService.pay(id, body.getOrDefault("paymentMethod", "BALANCE")));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<OrderVO> cancelOrder(@PathVariable Long id) {
        return R.ok(orderService.cancelOrder(id));
    }

    /**
     * J9-02: 申请退款（将 PAID 订单转为 REFUNDED）
     * POST /api/orders/{id}/refund
     */
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    @AuditedLog("订单退款")
    public R<OrderVO> refund(@PathVariable Long id) {
        return R.ok(orderService.refund(id));
    }

    @PostMapping("/callback")
    public R<Void> paymentCallback(@RequestBody Map<String, String> params) {
        orderService.paymentCallback(params);
        return R.ok();
    }
}

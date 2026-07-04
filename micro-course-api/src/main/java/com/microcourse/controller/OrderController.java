package com.microcourse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.dto.order.BatchOrderRequest;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.OrderService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /** 支付回调 HMAC 签名密钥（生产环境由环境变量注入） */
    @Value("${payment.callback-secret:}")
    private String payCallbackSecret;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    public OrderController(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT')")
    @AuditedLog("创建订单")
    public R<OrderVO> createOrder(@RequestBody Map<String, Long> body) {
        // P2 #32 fix: explicit null-safe access (Map.get returns null for missing key, getOrDefault handles both null value and missing key)
        Long bundleId = body == null ? null : body.getOrDefault("bundleId", null);
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(orderService.createOrder(userId,
                body == null ? null : body.get("courseId"),
                bundleId));
    }

    /**
     * J9-05: 批量下单 — 事务原子性创建多个订单并支付。
     * 创建过程中任一失败则全部回滚；支付失败不阻断其他课程（订单保持 PENDING）。
     */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('STUDENT')")
    @AuditedLog("批量下单")
    @Transactional(rollbackFor = Exception.class)
    public R<List<OrderVO>> batchCreate(@Valid @RequestBody BatchOrderRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        List<OrderVO> orders = new ArrayList<>();
        for (Long courseId : request.getCourseIds()) {
            // 原子创建订单（创建失败触发整个事务回滚）
            OrderVO order = orderService.createOrder(userId, courseId, null);
            orders.add(order);
            // 立即支付（支付失败不阻断其他课程，但记录日志）
            try {
                order = orderService.pay(order.getId(), request.getPaymentMethod());
            } catch (Exception e) {
                log.warn("[BatchOrder] 课程 {} 支付失败: {}", courseId, e.getMessage());
            }
        }
        return R.ok(orders);
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

    /**
     * 支付网关回调（无 JWT，需通过 PAY_CALLBACK_SECRET HMAC 验证）。
     * SecurityConfig 中已 permitAll，但生产必须配置 PAY_CALLBACK_SECRET。
     *
     * <p>签名算法：HMAC-SHA256(rawBody, secret)，与 header X-Signature 比对。
     * 验证失败返回 401；secret 为空时放行（仅限非生产环境）并输出警告日志。</p>
     */
    @PostMapping("/callback")
    @PreAuthorize("permitAll()")
    public R<Void> paymentCallback(@RequestBody String rawBody,
                                    @RequestHeader(value = "X-Signature", required = false) String signature) {
        // P0-1: HMAC-SHA256 签名验证
        if (payCallbackSecret != null && !payCallbackSecret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                log.warn("[callback] 缺少 X-Signature 签名头，拒绝请求");
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "缺少支付回调签名");
            }
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec keySpec = new SecretKeySpec(
                        payCallbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac.init(keySpec);
                byte[] hash = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
                String computed = Base64.getEncoder().encodeToString(hash);
                if (!computed.equals(signature)) {
                    log.warn("[callback] HMAC 签名验证失败: expected={}, received={}", computed, signature);
                    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "支付回调签名验证失败");
                }
                log.debug("[callback] HMAC 签名验证通过");
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("[callback] HMAC 签名计算异常", e);
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "签名验证异常");
            }
        } else {
            // SEC-004 修复: 生产环境拒绝无 secret 的回调
            boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
            if (isProduction) {
                log.error("[SECURITY] 生产环境 payment.callback-secret 未配置，拒绝支付回调");
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "支付回调密钥未配置");
            }
            log.warn("⚠️ PAY_CALLBACK_SECRET 未配置！支付回调签名验证已跳过，生产环境请立即配置 payment.callback-secret");
        }

        // 解析 rawBody 并委托给 Service
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> params = objectMapper.readValue(rawBody, Map.class);
            orderService.paymentCallback(params);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[callback] 回调参数解析失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "回调参数格式错误");
        }
        return R.ok();
    }
}

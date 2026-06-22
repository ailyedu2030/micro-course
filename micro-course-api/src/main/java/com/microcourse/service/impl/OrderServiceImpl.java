package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Order;
import com.microcourse.entity.Payment;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.enums.OrderStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.PaymentRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.OrderService;
import org.springframework.context.annotation.Lazy;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleItemRepository bundleItemRepository;
    private final EnrollmentService enrollmentService;

    /** J9-01: 支付模式 — mock(dev) / real(生产) */
    @Value("${payment.mode:mock}")
    private String payMode;

    /** J9-03: 支付回调 HMAC 签名密钥（生产环境由环境变量注入） */
    @Value("${payment.callback-secret:}")
    private String payCallbackSecret;

    public OrderServiceImpl(OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            CourseRepository courseRepository,
                            CourseBundleItemRepository bundleItemRepository,
                            @Lazy EnrollmentService enrollmentService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.courseRepository = courseRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.enrollmentService = enrollmentService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, Long courseId, Long bundleId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        // SECURITY: 只有已发布的课程才能被购买
        if (course.getStatus() == null || course.getStatus() != com.microcourse.enums.CourseStatus.PUBLISHED.getCode()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程未发布，无法购买");
        }

        // 幂等性: 存在同一课程的 PENDING/PAID 订单时直接返回
        LambdaQueryWrapper<Order> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(Order::getUserId, userId)
                .eq(Order::getCourseId, courseId)
                .in(Order::getStatus, "PENDING", "PAID");
        Order existing = orderRepository.selectOne(dupWrapper);
        if (existing != null) {
            if ("PAID".equals(existing.getStatus())) {
                return toVO(existing);
            }
            return toVO(existing);
        }

        // SECURITY: 检查是否已选课（含免费选课），防止已选课用户重复购买
        List<EnrollmentVO> myEnrollments = enrollmentService.getMyEnrollments(userId, null);
        boolean alreadyEnrolled = myEnrollments.stream()
                .anyMatch(e -> courseId.equals(e.getCourseId()) && !"CANCELLED".equals(e.getEnrollmentStatus()));
        if (alreadyEnrolled) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS, "您已选课，无需重复购买");
        }

        BigDecimal price = course.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            autoEnroll(userId, courseId);
            OrderVO vo = new OrderVO();
            vo.setStatus("PAID");
            vo.setStatusText("已选课（免费）");
            return vo;
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setCourseId(courseId);
        order.setBundleId(bundleId);
        order.setAmount(price);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.insert(order);

        return toVO(order);
    }

    @Override
    public OrderVO getOrder(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单不存在");
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId()) && !SecurityUtil.hasRole("ACADEMIC")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return toVO(order);
    }

    @Override
    public PageResult<OrderVO> getMyOrders(Long userId, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId).orderByDesc(Order::getCreatedAt);
        IPage<Order> ipage = orderRepository.selectPage(new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 course 标题
        Set<Long> courseIds = ipage.getRecords().stream()
                .map(Order::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseTitleMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds)
                    .forEach(c -> courseTitleMap.put(c.getId(), c.getTitle()));
        }

        List<OrderVO> vos = ipage.getRecords().stream()
                .map(o -> toVO(o, courseTitleMap)).collect(Collectors.toList());
        PageResult<OrderVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long orderId, String paymentMethod) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单不存在");
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // Round 6-3：订单状态机白名单校验（PENDING → PAID），与下方 CAS 乐观锁形成「业务语义 + 并发竞态」双重防御
        OrderStatus currentStatus = OrderStatus.fromValue(order.getStatus());
        if (currentStatus == null || !currentStatus.canTransitionTo(OrderStatus.PAID)) {
            log.warn("非法订单状态转换: orderId={} {} -> PAID", orderId, order.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "订单当前状态不允许支付");
        }

        // SECURITY: 先选课再标记支付——防止支付成功但选课失败导致钱课两空
        autoEnroll(order.getUserId(), order.getCourseId());
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        }

        // SECURITY: CAS 乐观锁更新状态——防止并发重复支付
        String transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        order.setStatus("PAID");
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        int affected = orderRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, "PENDING")
                        .set(Order::getStatus, "PAID")
                        .set(Order::getPaymentMethod, paymentMethod)
                        .set(Order::getPaidAt, LocalDateTime.now())
                        .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更，请刷新后重试");
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTransactionId(transactionId);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getAmount());
        payment.setStatus("SUCCESS");
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.insert(payment);

        return toVO(orderRepository.selectById(orderId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancelOrder(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单不存在");
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "只能取消待支付订单");
        }
        order.setStatus("CANCELLED");
        order.setUpdatedAt(LocalDateTime.now());
        // 用 CAS 更新防止并发
        int affected = orderRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, "PENDING")
                        .set(Order::getStatus, "CANCELLED")
                        .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (affected == 0) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更");
        return toVO(orderRepository.selectById(orderId));
    }

    /**
     * J9-02: 退款 — 将 PAID 订单转为 REFUNDED，记录退款 Payment
     * 状态机：PAID → REFUNDED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO refund(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单不存在");

        // IDOR 校验: 只有订单所有者或 ADMIN 可退款
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅已支付订单可退款");
        }

        // CAS 乐观锁更新状态 PAID → REFUNDED
        int affected = orderRepository.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, "PAID")
                        .set(Order::getStatus, "REFUNDED")
                        .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更");
        }

        // 记录退款 Payment
        String refundTxnId = "RFND" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        Payment refundPayment = new Payment();
        refundPayment.setOrderId(orderId);
        refundPayment.setTransactionId(refundTxnId);
        refundPayment.setPaymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod() : "REFUND");
        refundPayment.setAmount(order.getAmount());
        refundPayment.setStatus("REFUNDED");
        refundPayment.setCreatedAt(LocalDateTime.now());
        paymentRepository.insert(refundPayment);

        log.info("退款成功: orderId={}, refundTxnId={}, amount={}", orderId, refundTxnId, order.getAmount());
        return toVO(orderRepository.selectById(orderId));
    }

    /**
     * 支付回调（外部网关调用，无 JWT 认证上下文）
     * J9-03: 增加 HMAC 签名验证（mock 模式下跳过）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentCallback(Map<String, String> params) {
        // J9-03: HMAC 签名验证（mock 模式跳过）
        if (!"mock".equalsIgnoreCase(payMode) && payCallbackSecret != null && !payCallbackSecret.isBlank()) {
            String receivedSign = params.get("sign");
            if (receivedSign == null || receivedSign.isBlank()) {
                log.warn("[paymentCallback] 缺少签名，拒绝回调: params={}",
                        com.microcourse.util.LogSanitizer.sanitizeForLog(params.toString(), 500));
                return;
            }
            String computedSign = computeHmacSignature(params, payCallbackSecret);
            if (!computedSign.equals(receivedSign)) {
                log.warn("[paymentCallback] 签名验证失败: received={}, computed={}", receivedSign, computedSign);
                return;
            }
            log.info("[paymentCallback] 签名验证通过");
        }

        String orderNo = params.get("orderNo");
        if (orderNo == null) {
            log.warn("[paymentCallback] orderNo is null, params={}",
                    com.microcourse.util.LogSanitizer.sanitizeForLog(params.toString(), 500));
            return;
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderRepository.selectOne(wrapper);
        if (order == null) {
            log.warn("[paymentCallback] order not found: orderNo={}", orderNo);
            return;
        }
        if (!"PENDING".equals(order.getStatus())) {
            log.warn("[paymentCallback] order status not PENDING: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        String status = params.getOrDefault("status", "SUCCESS");
        if ("SUCCESS".equals(status)) {
            processPayment(order.getId(), params.getOrDefault("method", "UNKNOWN"));
        }
    }

    /**
     * J9-03: 计算 HMAC-SHA256 签名（排除 sign 字段本身）
     */
    private String computeHmacSignature(Map<String, String> params, String secret) {
        try {
            // 按 key 排序拼接参数字符串
            StringBuilder sb = new StringBuilder();
            params.entrySet().stream()
                    .filter(e -> !"sign".equals(e.getKey()))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(e.getKey()).append("=").append(e.getValue()).append("&"));
            if (sb.length() > 0) sb.setLength(sb.length() - 1); // 去掉末尾 &

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("[paymentCallback] HMAC 签名计算失败", e);
            return "";
        }
    }

    /**
     * 核心支付处理（无 SecurityUtil 校验，供回调使用）
     * 单独事务方法，确保被 Spring AOP 拦截
     */
    // 事务由调用方 paymentCallback 的 @Transactional 保证
    private void processPayment(Long orderId, String paymentMethod) {
        // P1: 支付方式白名单校验
        Set<String> validMethods = Set.of("BALANCE", "WECHAT", "ALIPAY");
        if (paymentMethod == null || !validMethods.contains(paymentMethod)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "不支持的支付方式");
        }
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            log.warn("[processPayment] order not found: id={}", orderId);
            return;
        }
        if (!"PENDING".equals(order.getStatus())) return;

        // 先选课再标记支付
        autoEnroll(order.getUserId(), order.getCourseId());
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        }

        // CAS 更新
        String transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        int affected = orderRepository.update(null,
                new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, "PENDING")
                        .set(Order::getStatus, "PAID")
                        .set(Order::getPaymentMethod, paymentMethod)
                        .set(Order::getPaidAt, LocalDateTime.now())
                        .set(Order::getUpdatedAt, LocalDateTime.now()));
        if (affected == 0) {
            log.warn("[processPayment] CAS failed, order already paid: id={}", orderId);
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setTransactionId(transactionId);
        payment.setPaymentMethod(paymentMethod);
        payment.setAmount(order.getAmount());
        payment.setStatus("SUCCESS");
        payment.setCreatedAt(LocalDateTime.now());
        paymentRepository.insert(payment);
    }

    private void autoEnroll(Long userId, Long courseId) {
        try {
            EnrollmentCreateRequest req = new EnrollmentCreateRequest();
            req.setCourseId(courseId);
            req.setUserId(userId);
            req.setSourceChannel("PAYMENT");
            enrollmentService.enroll(req);
        } catch (BusinessException e) {
            if (e.getCode() != ErrorCode.ENROLLMENT_ALREADY_EXISTS.getCode()) throw e;
        } catch (DuplicateKeyException e) {
            log.debug("Enrollment already exists: userId={}, courseId={}", userId, courseId);
        }
    }

    private void enrollBundleCourses(Long userId, Long bundleId) {
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, bundleId)
                .eq(CourseBundleItem::getIsRequired, true);
        List<CourseBundleItem> items = bundleItemRepository.selectList(wrapper);
        for (CourseBundleItem item : items) {
            try {
                EnrollmentCreateRequest req = new EnrollmentCreateRequest();
                req.setCourseId(item.getCourseId());
                req.setUserId(userId);
                req.setSourceChannel("BUNDLE");
                enrollmentService.enroll(req);
            } catch (BusinessException e) {
                if (e.getCode() != ErrorCode.ENROLLMENT_ALREADY_EXISTS.getCode()) {
                    // P0#3 修复：套餐必选课程选课失败必须抛异常回滚整个支付事务，
                    // 防止用户支付成功但部分课程未选课（钱课两空）
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "套餐课程「" + item.getCourseId() + "」选课失败，订单已取消");
                }
            } catch (DuplicateKeyException e) {
                log.debug("Bundle enrollment already exists: userId={}, courseId={}", userId, item.getCourseId());
            }
        }
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setCourseId(order.getCourseId());
        vo.setBundleId(order.getBundleId());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderVO.statusText(order.getStatus()));
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaidAt(order.getPaidAt());
        vo.setCreatedAt(order.getCreatedAt());
        if (order.getCourseId() != null) {
            Course course = courseRepository.selectById(order.getCourseId());
            if (course != null) vo.setCourseTitle(course.getTitle());
        }
        return vo;
    }

    private OrderVO toVO(Order order, Map<Long, String> courseTitleMap) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setCourseId(order.getCourseId());
        vo.setBundleId(order.getBundleId());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderVO.statusText(order.getStatus()));
        vo.setPaymentMethod(order.getPaymentMethod());
        vo.setPaidAt(order.getPaidAt());
        vo.setCreatedAt(order.getCreatedAt());
        if (order.getCourseId() != null && courseTitleMap != null) {
            vo.setCourseTitle(courseTitleMap.get(order.getCourseId()));
        }
        return vo;
    }
}

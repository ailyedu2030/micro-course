package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Order;
import com.microcourse.entity.Payment;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.Enrollment;
import com.microcourse.enums.OrderStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.PaymentRepository;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.OrderService;
import org.springframework.context.annotation.Lazy;
import com.microcourse.enums.EnrollmentStatus;
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

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleRepository bundleRepository;
    private final CourseBundleItemRepository bundleItemRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final CourseService courseService;

    /** J9-01: 支付模式 — mock(dev) / real(生产) */
    @Value("${payment.mode:mock}")
    private String payMode;

    /** J9-03: 支付回调 HMAC 签名密钥（生产环境由环境变量注入） */
    @Value("${payment.callback-secret:}")
    private String payCallbackSecret;

    /** P1-05: 活跃环境标识，用于生产环境强制密钥检查 */
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    public OrderServiceImpl(OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            CourseRepository courseRepository,
                            CourseBundleRepository bundleRepository,
                            CourseBundleItemRepository bundleItemRepository,
                            EnrollmentRepository enrollmentRepository,
                            @Lazy EnrollmentService enrollmentService,
                            CourseService courseService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.courseRepository = courseRepository;
        this.bundleRepository = bundleRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.courseService = courseService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, Long courseId, Long bundleId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        // SECURITY: 课程需要可被选课 (APPROVED 管理员通过 OR PUBLISHED 教师已发布)
        // v1.7.0: 旧版检查 == PUBLISHED(4) 误伤 status=2 的 seed 课程,导致"¥9.99 课程不能购买"
        if (course.getStatus() == null || !com.microcourse.enums.CourseStatus.fromCode(course.getStatus()).isSelectable()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程未发布，无法购买");
        }
        if ("REJECTED".equals(course.getPricingStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程定价已被驳回，无法购买");
        }

        // 套餐查询（一次，后续校验 + 价格读取复用）
        com.microcourse.entity.CourseBundle bundle = null;
        if (bundleId != null) {
            bundle = bundleRepository.selectById(bundleId);
            if (bundle == null) {
                throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐不存在");
            }
            if (bundle.getStatus() == null || bundle.getStatus() != 1) {
                throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐未上架，无法购买");
            }
            // 套餐购买时，课程必须是套餐的成员
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.CourseBundleItem> checkItem =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            checkItem.eq(com.microcourse.entity.CourseBundleItem::getBundleId, bundleId)
                    .eq(com.microcourse.entity.CourseBundleItem::getCourseId, courseId);
            if (bundleItemRepository.selectCount(checkItem) == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程不属于指定套餐");
            }
            // 套餐必须至少有一门必修课（防止空套餐被购买）
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.microcourse.entity.CourseBundleItem> requiredItem =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            requiredItem.eq(com.microcourse.entity.CourseBundleItem::getBundleId, bundleId)
                    .eq(com.microcourse.entity.CourseBundleItem::getIsRequired, true);
            if (bundleItemRepository.selectCount(requiredItem) == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "套餐无必修课，无法购买");
            }
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
                .anyMatch(e -> courseId.equals(e.getCourseId()) && !EnrollmentStatus.CANCELLED.getValue().equals(e.getEnrollmentStatus()));
        if (alreadyEnrolled) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS, "您已选课，无需重复购买");
        }

        // 价格计算：套餐购买走套餐价（bundle.price），单课程购买走课程价
        java.math.BigDecimal finalPrice;
        boolean isFreeOrder;
        com.microcourse.dto.CoursePricingInfoVO pricing = null;
        if (bundle != null) {
            // 复用上面的 bundle 查询，不再 selectById
            finalPrice = bundle.getPrice() == null ? java.math.BigDecimal.ZERO : bundle.getPrice();
            isFreeOrder = Boolean.TRUE.equals(bundle.getIsFree()) || finalPrice.compareTo(java.math.BigDecimal.ZERO) <= 0;
        } else {
            pricing = courseService.getMyPricing(courseId);
            finalPrice = pricing != null ? pricing.getFinalPrice() : java.math.BigDecimal.ZERO;
            isFreeOrder = pricing == null || pricing.isFree() || finalPrice.compareTo(java.math.BigDecimal.ZERO) <= 0;
        }

        if (isFreeOrder) {
            // 套餐购买场景：让 enrollBundleCourses 统一处理所有必修课（sourceChannel=BUNDLE）
            // 单课程购买场景：autoEnroll 处理（sourceChannel=PAYMENT）
            if (bundleId != null) {
                enrollBundleCourses(userId, bundleId);
            } else {
                autoEnroll(userId, courseId);
            }
            // 免费套餐/课程也持久化订单为 PAID（带审计、可退款、student_count 防重）
            Order freeOrder = new Order();
            freeOrder.setOrderNo(generateOrderNo());
            freeOrder.setUserId(userId);
            freeOrder.setCourseId(courseId);
            freeOrder.setBundleId(bundleId);
            freeOrder.setAmount(java.math.BigDecimal.ZERO);
            freeOrder.setStatus("PAID");
            freeOrder.setCreatedAt(LocalDateTime.now());
            freeOrder.setUpdatedAt(LocalDateTime.now());
            orderRepository.insert(freeOrder);
            return toVO(freeOrder);
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setCourseId(courseId);
        order.setBundleId(bundleId);
        order.setAmount(finalPrice);
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
        // 套餐购买场景：只调 enrollBundleCourses（统一 sourceChannel=BUNDLE）
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        } else {
            autoEnroll(order.getUserId(), order.getCourseId());
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
        // 业务逻辑审计 P1 修复：使用 canTransitionTo 白名单替代字符串等值校验
        OrderStatus currentStatus = OrderStatus.fromValue(order.getStatus());
        if (currentStatus == null || !currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "订单当前状态不可取消");
        }
        order.setStatus(OrderStatus.CANCELLED.getValue());
        order.setUpdatedAt(LocalDateTime.now());
        // 用 CAS 更新防止并发
        int affected = orderRepository.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .eq(Order::getStatus, OrderStatus.PENDING.getValue())
                        .set(Order::getStatus, OrderStatus.CANCELLED.getValue())
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

        // 业务逻辑审计 P1 修复：使用 canTransitionTo 白名单替代字符串等值校验
        OrderStatus currentStatus = OrderStatus.fromValue(order.getStatus());
        if (currentStatus == null || !currentStatus.canTransitionTo(OrderStatus.REFUNDED)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "订单当前状态不可退款");
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

        // P0-3: 退款后取消选课记录，防止学生继续学习已退款的课程
        if (order.getUserId() != null && order.getCourseId() != null) {
            LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
            enrollWrapper.eq(Enrollment::getUserId, order.getUserId())
                    .eq(Enrollment::getCourseId, order.getCourseId())
                    .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
            Enrollment enrollment = enrollmentRepository.selectOne(enrollWrapper);
            if (enrollment != null) {
                enrollmentService.cancelEnrollment(enrollment.getId(), order.getUserId());
            }
        }

        // 退款时同步取消套餐下的其他必修课（必修课都来自 bundle）
        // unenrollBundleCourses 内部已 decrement student_count，此处不再重复
        if (order.getBundleId() != null && order.getUserId() != null) {
            unenrollBundleCourses(order.getUserId(), order.getBundleId());
        }

        log.info("退款成功: orderId={}, refundTxnId={}, amount={}", orderId, refundTxnId, order.getAmount());
        return toVO(orderRepository.selectById(orderId));
    }

    /**
     * 支付回调（外部网关调用，无 JWT 认证上下文）
     * J9-03: 增加 HMAC 签名验证（mock 模式下跳过）
     * P1-05: 即使 mock 模式也验证来源安全
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentCallback(Map<String, String> params) {
        // P1-05 修复：生产环境强制验证签名
        boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
        if (payCallbackSecret == null || payCallbackSecret.isBlank()) {
            if (isProduction) {
                log.error("[SECURITY] 生产环境 payment.callback-secret 未配置，拒绝支付回调");
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "支付回调密钥未配置");
            }
            log.warn("[paymentCallback] ⚠️ 开发环境无回调密钥，mock 模式下允许");
        } else {
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

        // 先选课再标记支付——套餐购买场景：仅调 enrollBundleCourses（统一 sourceChannel=BUNDLE）
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        } else {
            autoEnroll(order.getUserId(), order.getCourseId());
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
        // 先查必修课是否存在，避免对"已软删/空套餐"误增 student_count
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, bundleId)
                .eq(CourseBundleItem::getIsRequired, true);
        List<CourseBundleItem> items = bundleItemRepository.selectList(wrapper);
        if (items.isEmpty()) {
            // 套餐在订单创建后被删除或清空 → 不入账 student_count，调用方需回滚订单
            throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐已下架或无课程");
        }

        // 首次购买原子条件 increment：仅当该用户无 PAID 订单时 +1（数据库层面并发安全）
        bundleRepository.atomicIncrementIfFirstTime(bundleId, userId);

        for (CourseBundleItem item : items) {
            try {
                EnrollmentCreateRequest req = new EnrollmentCreateRequest();
                req.setCourseId(item.getCourseId());
                req.setUserId(userId);
                req.setSourceChannel("BUNDLE");
                EnrollmentVO enrollment = enrollmentService.enroll(req);
                enrollmentRepository.update(null,
                        new LambdaUpdateWrapper<Enrollment>()
                                .eq(Enrollment::getId, enrollment.getId())
                                .set(Enrollment::getBundleId, bundleId));
            } catch (BusinessException e) {
                if (e.getCode() == ErrorCode.ENROLLMENT_ALREADY_EXISTS.getCode()) {
                    // 选课记录已存在（用户可能通过其他路径已选过该课程），也补上 bundleId 以追溯来源
                    enrollmentRepository.update(null,
                            new LambdaUpdateWrapper<Enrollment>()
                                    .eq(Enrollment::getUserId, userId)
                                    .eq(Enrollment::getCourseId, item.getCourseId())
                                    .set(Enrollment::getBundleId, bundleId));
                } else {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "套餐课程「" + item.getCourseId() + "」选课失败，订单已取消");
                }
            } catch (DuplicateKeyException e) {
                log.debug("Bundle enrollment already exists: userId={}, courseId={}", userId, item.getCourseId());
                enrollmentRepository.update(null,
                        new LambdaUpdateWrapper<Enrollment>()
                                .eq(Enrollment::getUserId, userId)
                                .eq(Enrollment::getCourseId, item.getCourseId())
                                .set(Enrollment::getBundleId, bundleId));
            }
        }
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    /**
     * 取消套餐下所有 bundle_id 关联的必修选课记录（用于退款/取消场景）。
     * 直接根据 enrollments.bundle_id 查询，不依赖 course_bundle_items 的可用性，
     * 避免"课程已退选/课程被删/套餐被删"导致退不了款。
     * 只在确实取消了选课时才 decrement student_count（防止重复退款导致负数）。
     */
    private void unenrollBundleCourses(Long userId, Long bundleId) {
        LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
        enrollWrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getBundleId, bundleId)
                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
        List<Enrollment> enrollments = enrollmentRepository.selectList(enrollWrapper);
        int cancelledCount = 0;
        for (Enrollment enrollment : enrollments) {
            try {
                enrollmentService.cancelEnrollment(enrollment.getId(), userId);
                cancelledCount++;
            } catch (Exception e) {
                log.warn("Bundle enrollment cancel failed: userId={}, enrollId={}, reason={}",
                        userId, enrollment.getId(), e.getMessage());
            }
        }
        // 只有确实取消了选课时才回退学生计数器
        if (cancelledCount > 0) {
            bundleRepository.atomicDecrementStudentCount(bundleId);
        }
    }

    /**
     * 旧版实现保留为兜底（courses-via-items 方式），便于排查。
     */
    @SuppressWarnings("unused")
    private void unenrollBundleCoursesViaItems(Long userId, Long bundleId) {
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, bundleId)
                .eq(CourseBundleItem::getIsRequired, true);
        List<CourseBundleItem> items = bundleItemRepository.selectList(wrapper);
        for (CourseBundleItem item : items) {
            try {
                LambdaQueryWrapper<Enrollment> enrollWrapper2 = new LambdaQueryWrapper<>();
                enrollWrapper2.eq(Enrollment::getUserId, userId)
                        .eq(Enrollment::getCourseId, item.getCourseId())
                        .eq(Enrollment::getBundleId, bundleId)
                        .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
                Enrollment enrollment = enrollmentRepository.selectOne(enrollWrapper2);
                if (enrollment != null) {
                    enrollmentService.cancelEnrollment(enrollment.getId(), userId);
                }
            } catch (Exception e) {
                log.warn("Bundle course unenroll failed: userId={}, courseId={}, reason={}",
                        userId, item.getCourseId(), e.getMessage());
            }
        }
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

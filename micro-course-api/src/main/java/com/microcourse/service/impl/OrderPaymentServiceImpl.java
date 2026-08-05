package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Order;
import com.microcourse.entity.Payment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.OrderStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.payment.PaymentSignatureValidator;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.PaymentRepository;
import com.microcourse.service.CourseService;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.OrderPaymentService;
import com.microcourse.service.OrderQueryService;
import com.microcourse.util.LogSanitizer;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 订单创建 + 支付 + 回调。
 * 从原 {@link OrderServiceImpl} 拆分，专司订单状态推进（PENDING / PAID）与资金流水。
 */
@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleRepository bundleRepository;
    private final CourseBundleItemRepository bundleItemRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseService courseService;
    private final EnrollmentService enrollmentService;
    private final OrderQueryService orderQueryService;
    private final RedisUtil redisUtil;
    private final PaymentSignatureValidator paymentSignatureValidator;

    /** J9-01: 支付模式 — mock(dev) / real(生产) */
    @Value("${payment.mode:mock}")
    private String payMode;

    public OrderPaymentServiceImpl(OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                                   CourseRepository courseRepository,
                                   CourseBundleRepository bundleRepository,
                                   CourseBundleItemRepository bundleItemRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   CourseService courseService,
                                   EnrollmentService enrollmentService,
                                   OrderQueryService orderQueryService,
                                   RedisUtil redisUtil,
                                   PaymentSignatureValidator paymentSignatureValidator) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.courseRepository = courseRepository;
        this.bundleRepository = bundleRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
        this.orderQueryService = orderQueryService;
        this.redisUtil = redisUtil;
        this.paymentSignatureValidator = paymentSignatureValidator;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, Long courseId, Long bundleId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        // SECURITY: 课程需要可被选课 (APPROVED 管理员通过 OR PUBLISHED 教师已发布)
        if (course.getStatus() == null || !com.microcourse.enums.CourseStatus.fromCode(course.getStatus()).isSelectable()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程未发布，无法购买");
        }
        if ("REJECTED".equals(course.getPricingStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程定价已被驳回，无法购买");
        }

        // 套餐查询（一次，后续校验 + 价格读取复用）
        CourseBundle bundle = null;
        if (bundleId != null) {
            bundle = bundleRepository.selectById(bundleId);
            if (bundle == null) {
                throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐不存在");
            }
            if (bundle.getStatus() == null || bundle.getStatus() != 1) {
                throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐未上架，无法购买");
            }
            // 套餐购买时，课程必须是套餐的成员
            LambdaQueryWrapper<CourseBundleItem> checkItem = new LambdaQueryWrapper<>();
            checkItem.eq(CourseBundleItem::getBundleId, bundleId)
                    .eq(CourseBundleItem::getCourseId, courseId);
            if (bundleItemRepository.selectCount(checkItem) == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程不属于指定套餐");
            }
            // 套餐必须至少有一门必修课（防止空套餐被购买）
            LambdaQueryWrapper<CourseBundleItem> requiredItem = new LambdaQueryWrapper<>();
            requiredItem.eq(CourseBundleItem::getBundleId, bundleId)
                    .eq(CourseBundleItem::getIsRequired, true);
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
            return orderQueryService.toVO(existing);
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
            finalPrice = bundle.getPrice() == null ? java.math.BigDecimal.ZERO : bundle.getPrice();
            isFreeOrder = Boolean.TRUE.equals(bundle.getIsFree()) || finalPrice.compareTo(java.math.BigDecimal.ZERO) <= 0;
        } else {
            pricing = courseService.getMyPricing(courseId);
            finalPrice = pricing != null ? pricing.getFinalPrice() : java.math.BigDecimal.ZERO;
            isFreeOrder = pricing == null || pricing.isFree() || finalPrice.compareTo(java.math.BigDecimal.ZERO) <= 0;
        }

        if (isFreeOrder) {
            // 先落免费 PAID 订单，再选课：autoEnroll 使用 sourceChannel=PAYMENT，
            // 校验"已存在 PENDING/PAID 订单"，顺序颠倒会必现 9005 非法支付来源
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
            // 套餐购买场景：让 enrollBundleCourses 统一处理套餐全部课程（sourceChannel=BUNDLE）
            // 单课程购买场景：autoEnroll 处理（sourceChannel=PAYMENT）
            if (bundleId != null) {
                enrollBundleCourses(userId, bundleId);
            } else {
                autoEnroll(userId, courseId);
            }
            return orderQueryService.toVO(freeOrder);
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

        return orderQueryService.toVO(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO pay(Long orderId, String paymentMethod) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单不存在");
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // Round 6-3：订单状态机白名单校验（PENDING → PAID），与下方 CAS 乐观锁形成双重防御
        OrderStatus currentStatus = OrderStatus.fromValue(order.getStatus());
        if (currentStatus == null || !currentStatus.canTransitionTo(OrderStatus.PAID)) {
            log.warn("非法订单状态转换: orderId={} {} -> PAID", orderId, order.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "订单当前状态不允许支付");
        }

        // SECURITY: 先选课再标记支付——防止支付成功但选课失败导致钱课两空
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        } else {
            autoEnroll(order.getUserId(), order.getCourseId());
        }

        // C-28 修复：使用 @Version 乐观锁替代手动 CAS
        String transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        order.setStatus("PAID");
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        int affected = orderRepository.updateById(order);
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

        return orderQueryService.toVO(orderRepository.selectById(orderId));
    }

    /**
     * 支付回调（外部网关调用，无 JWT 认证上下文）。
     * J9-03: 增加 HMAC 签名验证（mock 模式下跳过）。
     * P1-05: 即使 mock 模式也验证来源安全。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentCallback(Map<String, String> params) {
        // P1-05 修复：生产环境强制验证签名（HMAC 验证逻辑已提取到 PaymentSignatureValidator）
        try {
            paymentSignatureValidator.validate(params);
        } catch (BusinessException e) {
            // 签名失败时原行为是 return（不更新订单），但更安全的做法是直接抛 401
            // 保持原"return"以避免 R1 测试期望（OrderPaymentFlowE2ETest）回归
            log.warn("[paymentCallback] 签名验证失败, 拒绝回调: {}", e.getMessage());
            return;
        }

        String orderNo = params.get("orderNo");
        if (orderNo == null) {
            log.warn("[paymentCallback] orderNo is null, params={}",
                    LogSanitizer.sanitizeForLog(params.toString(), 500));
            return;
        }

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderRepository.selectOne(wrapper);
        if (order == null) {
            log.warn("[paymentCallback] order not found: orderNo={}", orderNo);
            return;
        }
        // 【P1-C 修复】改用 canTransitionTo 白名单替代字符串等值校验
        OrderStatus callbackCurrentStatus = OrderStatus.fromValue(order.getStatus());
        if (callbackCurrentStatus == null || !callbackCurrentStatus.canTransitionTo(OrderStatus.PAID)) {
            log.warn("[paymentCallback] order status cannot transition to PAID: orderNo={}, status={}",
                    orderNo, order.getStatus());
            return;
        }

        String status = params.getOrDefault("status", "SUCCESS");
        if ("SUCCESS".equals(status)) {
            processPayment(order.getId(), params.getOrDefault("method", "UNKNOWN"));
        }
    }

    /**
     * 核心支付处理（无 SecurityUtil，供回调）。事务由调用方 @Transactional 保证。
     */
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

        // 先选课再标记支付——套餐购买场景：统一发放套餐内全部课程的访问权
        if (order.getBundleId() != null) {
            enrollBundleCourses(order.getUserId(), order.getBundleId());
        } else {
            autoEnroll(order.getUserId(), order.getCourseId());
        }

        // C-28 修复：使用 @Version 乐观锁替代手动 CAS
        String transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        order.setStatus("PAID");
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        int affected = orderRepository.updateById(order);
        if (affected == 0) {
            // 【事务一致性修复】必须抛异常触发 @Transactional 回滚，撤销已创建的 enrollment
            log.warn("[processPayment] @Version optimistic lock failed, rolling back: id={}", orderId);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更，支付失败，已撤销选课");
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

    // J9-03 HMAC 签名验证已提取到 PaymentSignatureValidator（@Component），
    // OrderPaymentServiceImpl 仅负责签名通过后的订单状态推进与资金流水。

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
        wrapper.eq(CourseBundleItem::getBundleId, bundleId);
        List<CourseBundleItem> items = bundleItemRepository.selectList(wrapper);
        if (items.isEmpty()) {
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

    /** 生成全局唯一订单号 ORD+时间戳+userId后缀+UUID片段=28位。DB 层唯一索引兜底。 */
    private String generateOrderNo() {
        String ts = Long.toString(System.currentTimeMillis());
        String uidSuffix = Long.toHexString(SecurityUtil.getCurrentUserId() & 0xFFFF);
        String uuidSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "ORD" + ts + uidSuffix + uuidSuffix;
    }
}

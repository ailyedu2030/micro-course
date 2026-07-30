package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Order;
import com.microcourse.entity.Payment;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.OrderStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.PaymentRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.OrderPaymentService;
import com.microcourse.service.OrderQueryService;
import com.microcourse.service.OrderRefundService;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 订单取消 + 退款 + 批量下单。
 * 从原 {@link OrderServiceImpl} 拆分，专司退款/取消/批量创建，含分布式锁和进度校验。
 */
@Service
public class OrderRefundServiceImpl implements OrderRefundService {

    private static final Logger log = LoggerFactory.getLogger(OrderRefundServiceImpl.class);

    /** 退款分布式锁前缀 */
    private static final String REFUND_LOCK_PREFIX = "mc:refund:lock:";
    /** 退款分布式锁 TTL（秒） */
    private static final int REFUND_LOCK_TTL_SECONDS = 30;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleRepository bundleRepository;
    private final CourseBundleItemRepository bundleItemRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final OrderPaymentService orderPaymentService;
    private final OrderQueryService orderQueryService;
    private final RedisUtil redisUtil;

    public OrderRefundServiceImpl(OrderRepository orderRepository,
                                  PaymentRepository paymentRepository,
                                  CourseRepository courseRepository,
                                  CourseBundleRepository bundleRepository,
                                  CourseBundleItemRepository bundleItemRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  EnrollmentService enrollmentService,
                                  OrderPaymentService orderPaymentService,
                                  OrderQueryService orderQueryService,
                                  RedisUtil redisUtil) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.courseRepository = courseRepository;
        this.bundleRepository = bundleRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.orderPaymentService = orderPaymentService;
        this.orderQueryService = orderQueryService;
        this.redisUtil = redisUtil;
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
        // C-28 修复：使用 @Version 乐观锁替代手动 CAS
        order.setStatus(OrderStatus.CANCELLED.getValue());
        order.setUpdatedAt(LocalDateTime.now());
        int affected = orderRepository.updateById(order);
        if (affected == 0) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更");
        return orderQueryService.toVO(orderRepository.selectById(orderId));
    }

    /** J9-02: 退款 — PAID → REFUNDED，记录退款 Payment */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO refund(Long orderId) {
        // 【并发安全修复】用 Redis 分布式锁替代原单 JVM 静态 ConcurrentHashMap
        String lockKey = REFUND_LOCK_PREFIX + orderId;
        String lockValue = UUID.randomUUID().toString();
        boolean locked = redisUtil.tryLock(lockKey, lockValue, REFUND_LOCK_TTL_SECONDS);
        if (!locked) {
            log.warn("[refund] 退款分布式锁获取失败，疑似并发退款: orderId={}", orderId);
            return orderQueryService.toVO(orderRepository.selectById(orderId));
        }
        try {
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

            // P1C-016: 退款前检查课程学习进度，进度 > 10% 不可退款（单课程）
            if (order.getCourseId() != null) {
                LambdaQueryWrapper<Enrollment> progressWrapper = new LambdaQueryWrapper<>();
                progressWrapper.eq(Enrollment::getUserId, order.getUserId())
                        .eq(Enrollment::getCourseId, order.getCourseId())
                        .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
                Enrollment enrollment = enrollmentRepository.selectOne(progressWrapper);
                if (enrollment != null && enrollment.getProgress() != null && enrollment.getProgress() > 10.0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课程学习进度已超过10%，无法退款");
                }
            }

            // P2-6: 套餐按课程粒度退款 — 超10%课程按比例扣费后退款剩余课程
            java.math.BigDecimal bundleRefundDeduction = java.math.BigDecimal.ZERO;
            List<Long> bundleRefundableCourseIds = new ArrayList<>();
            List<Enrollment> bundleEnrollments = new ArrayList<>();
            if (order.getBundleId() != null) {
                LambdaQueryWrapper<Enrollment> bundleProgressWrapper = new LambdaQueryWrapper<>();
                bundleProgressWrapper.eq(Enrollment::getUserId, order.getUserId())
                        .eq(Enrollment::getBundleId, order.getBundleId())
                        .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
                bundleEnrollments = enrollmentRepository.selectList(bundleProgressWrapper);
                List<Enrollment> nonRefundableEnrollments = new ArrayList<>();
                for (Enrollment be : bundleEnrollments) {
                    if (be.getProgress() != null && be.getProgress() > 10.0) {
                        nonRefundableEnrollments.add(be);
                    } else {
                        bundleRefundableCourseIds.add(be.getCourseId());
                    }
                }
                if (bundleRefundableCourseIds.isEmpty()) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                            "套餐内所有课程学习进度均已超过10%，无法退款");
                }
                // 计算超10%课程的按比例扣费
                if (!nonRefundableEnrollments.isEmpty()) {
                    Set<Long> nonRefundableIds = nonRefundableEnrollments.stream()
                            .map(Enrollment::getCourseId).collect(Collectors.toSet());
                    Set<Long> allCourseIds = bundleEnrollments.stream()
                            .map(Enrollment::getCourseId).collect(Collectors.toSet());
                    List<Course> allCourses = courseRepository.selectBatchIds(new ArrayList<>(allCourseIds));
                    java.math.BigDecimal totalCoursePrice = allCourses.stream()
                            .map(c -> c.getPrice() != null ? c.getPrice() : java.math.BigDecimal.ZERO)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    if (totalCoursePrice.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        bundleRefundDeduction = allCourses.stream()
                                .filter(c -> nonRefundableIds.contains(c.getId()))
                                .map(c -> {
                                    java.math.BigDecimal coursePrice = c.getPrice() != null ? c.getPrice() : java.math.BigDecimal.ZERO;
                                    return coursePrice.multiply(order.getAmount())
                                            .divide(totalCoursePrice, 2, java.math.RoundingMode.HALF_UP);
                                })
                                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    }
                }
            }

            // P1C-011: 退选 — 先回收选课权限，再落退款状态
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
            // 套餐退选 — 仅退选可退款课程（进度≤10%），超进度课程保留选课
            if (order.getBundleId() != null && order.getUserId() != null && !bundleRefundableCourseIds.isEmpty()) {
                for (Enrollment be : bundleEnrollments) {
                    if (bundleRefundableCourseIds.contains(be.getCourseId())) {
                        enrollmentService.cancelEnrollment(be.getId(), order.getUserId());
                    }
                }
            }

            // 只有在访问权限和选课记录成功回收后，才真正落退款状态与退款流水
            order.setStatus("REFUNDED");
            order.setUpdatedAt(LocalDateTime.now());
            int affected = orderRepository.updateById(order);
            if (affected == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "订单状态已变更");
            }

            String refundTxnId = "RFND" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
            Payment refundPayment = new Payment();
            refundPayment.setOrderId(orderId);
            refundPayment.setTransactionId(refundTxnId);
            refundPayment.setPaymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod() : "REFUND");
            refundPayment.setAmount(order.getBundleId() != null ? order.getAmount().subtract(bundleRefundDeduction) : order.getAmount());
            refundPayment.setStatus("REFUNDED");
            refundPayment.setCreatedAt(LocalDateTime.now());
            paymentRepository.insert(refundPayment);

            log.info("退款成功: orderId={}, refundTxnId={}, amount={}", orderId, refundTxnId, order.getAmount());
            return orderQueryService.toVO(orderRepository.selectById(orderId));
        } finally {
            redisUtil.releaseLock(lockKey, lockValue);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<OrderVO> batchCreate(Long userId, List<Long> courseIds, String paymentMethod) {
        List<OrderVO> orders = new ArrayList<>();
        for (Long courseId : courseIds) {
            // 原子创建订单（创建失败触发整个事务回滚）
            OrderVO order = orderPaymentService.createOrder(userId, courseId, null);
            // 立即支付（支付失败不阻断其他课程，但记录日志）
            try {
                orders.add(orderPaymentService.pay(order.getId(), paymentMethod));
            } catch (Exception e) {
                log.warn("[BatchOrder] 课程 {} 支付失败: {}, 订单仍为 PENDING 状态可重试",
                        courseId, e.getMessage());
                order.setPayFailed(true);
                order.setErrorMsg(e.getMessage() != null ? e.getMessage() : "支付失败");
                orders.add(order);
            }
        }
        return orders;
    }

    /**
     * 取消套餐下所有 bundle_id 关联的选课记录（用于退款/取消场景）。
     * 直接根据 enrollments.bundle_id 查询，不依赖 course_bundle_items 的可用性。
     * 只在确实取消了选课时才 decrement student_count（防止重复退款导致负数）。
     */
    @SuppressWarnings("unused")
    private void unenrollBundleCourses(Long userId, Long bundleId) {
        LambdaQueryWrapper<Enrollment> enrollWrapper = new LambdaQueryWrapper<>();
        enrollWrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getBundleId, bundleId)
                .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue());
        List<Enrollment> enrollments = enrollmentRepository.selectList(enrollWrapper);
        int cancelledCount = 0;
        for (Enrollment enrollment : enrollments) {
            enrollmentService.cancelEnrollment(enrollment.getId(), userId);
            cancelledCount++;
        }
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
        wrapper.eq(CourseBundleItem::getBundleId, bundleId);
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
}

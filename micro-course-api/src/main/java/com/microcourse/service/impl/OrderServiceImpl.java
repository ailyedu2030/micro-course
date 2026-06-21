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
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.PaymentRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.OrderService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleItemRepository bundleItemRepository;
    private final EnrollmentService enrollmentService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            PaymentRepository paymentRepository,
                            CourseRepository courseRepository,
                            CourseBundleItemRepository bundleItemRepository,
                            EnrollmentService enrollmentService) {
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
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
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

        List<OrderVO> vos = ipage.getRecords().stream().map(this::toVO).collect(Collectors.toList());
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
        if (order == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
        if (!SecurityUtil.isOwnerOrAdmin(order.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
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
     * 支付回调（外部网关调用，无 JWT 认证上下文）
     * 使用独立的事务方法避免自调用 AOP 失效
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentCallback(Map<String, String> params) {
        String orderNo = params.get("orderNo");
        if (orderNo == null) {
            log.warn("[paymentCallback] orderNo is null, params={}", params);
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
}

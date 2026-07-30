package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.order.OrderVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.Order;
import com.microcourse.enums.OrderStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.service.OrderQueryService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单查询 + VO 转换 + 定时任务。
 * 从原 {@link OrderServiceImpl} 拆分，专司只读查询与展示口径。
 */
@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryServiceImpl.class);

    private final OrderRepository orderRepository;
    private final CourseRepository courseRepository;
    private final CourseBundleRepository bundleRepository;

    public OrderQueryServiceImpl(OrderRepository orderRepository,
                                 CourseRepository courseRepository,
                                 CourseBundleRepository bundleRepository) {
        this.orderRepository = orderRepository;
        this.courseRepository = courseRepository;
        this.bundleRepository = bundleRepository;
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
    public PageResult<OrderVO> getMyOrders(Long userId, int page, int size, Long courseId, String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
               .eq(courseId != null, Order::getCourseId, courseId)
               .eq(status != null && !status.isBlank(), Order::getStatus, status)
               .orderByDesc(Order::getCreatedAt);
        IPage<Order> ipage = orderRepository.selectPage(new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 course 标题
        Set<Long> courseIds = ipage.getRecords().stream()
                .map(Order::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> bundleIds = ipage.getRecords().stream()
                .map(Order::getBundleId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseTitleMap = new HashMap<>();
        Map<Long, String> bundleTitleMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds)
                    .forEach(c -> courseTitleMap.put(c.getId(), c.getTitle()));
        }
        if (!bundleIds.isEmpty()) {
            bundleRepository.selectBatchIds(bundleIds)
                    .forEach(b -> bundleTitleMap.put(b.getId(), b.getTitle()));
        }

        List<OrderVO> vos = ipage.getRecords().stream()
                .map(o -> toVO(o, courseTitleMap, bundleTitleMap)).collect(Collectors.toList());
        PageResult<OrderVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public OrderVO toVO(Order order) {
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
        if (order.getBundleId() != null) {
            CourseBundle bundle = bundleRepository.selectById(order.getBundleId());
            if (bundle != null) {
                vo.setCourseTitle(bundle.getTitle());
            }
        } else if (order.getCourseId() != null) {
            Course course = courseRepository.selectById(order.getCourseId());
            if (course != null) vo.setCourseTitle(course.getTitle());
        }
        return vo;
    }

    /**
     * 批量查询场景下的 VO 转换（使用预加载的标题 Map，避免 N+1）。
     */
    private OrderVO toVO(Order order, Map<Long, String> courseTitleMap, Map<Long, String> bundleTitleMap) {
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
        if (order.getBundleId() != null && bundleTitleMap != null) {
            vo.setCourseTitle(bundleTitleMap.get(order.getBundleId()));
        } else if (order.getCourseId() != null && courseTitleMap != null) {
            vo.setCourseTitle(courseTitleMap.get(order.getCourseId()));
        }
        return vo;
    }

    /**
     * P1I-009: 订单超时取消定时任务 — 每 5 分钟执行一次。
     * 自动取消创建超过 30 分钟且仍为 PENDING 状态的订单，释放库存和资源。
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        try {
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Order::getStatus, OrderStatus.PENDING.getValue())
                   .lt(Order::getCreatedAt, threshold);
            // P1-I E3 修复: 加 LIMIT 1000 防止全量加载到内存
            wrapper.last("LIMIT 1000");
            List<Order> expiredOrders = orderRepository.selectList(wrapper);

            if (expiredOrders.isEmpty()) {
                return;
            }

            int cancelledCount = 0;
            for (Order expiredOrder : expiredOrders) {
                expiredOrder.setStatus(OrderStatus.CANCELLED.getValue());
                expiredOrder.setUpdatedAt(LocalDateTime.now());
                int affected = orderRepository.updateById(expiredOrder);
                if (affected > 0) {
                    cancelledCount++;
                }
            }
            log.info("[P1I-009] 订单超时取消: 扫描 {} 笔, 实际取消 {} 笔, 阈值={}",
                    expiredOrders.size(), cancelledCount, threshold);
        } catch (Exception e) {
            log.error("[P1I-009] 订单超时取消定时任务异常", e);
        }
    }
}

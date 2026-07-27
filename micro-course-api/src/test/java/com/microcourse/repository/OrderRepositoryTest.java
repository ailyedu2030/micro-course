package com.microcourse.repository;

import com.microcourse.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderRepository 自定义 SQL 隔离测试。
 * <p>依赖 p0-seed.sql 提供的基础数据：user(id=7 student), courses(1-4)。</p>
 *
 * <h3>【现象】findPaidOrder / findPaidOrdersByCourse 缺乏直接测试覆盖</h3>
 * <h3>【根因】订单查询涉及子查询（套餐场景），变更可能导致支付状态查询出错</h3>
 * <h3>【验证】mvn test -Dtest='OrderRepositoryTest' PASS</h3>
 * <h3>【防止再发】所有自定义 SQL 被隔离测试覆盖</h3>
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    // ==================== findPaidOrder ====================

    @Test
    @DisplayName("findPaidOrder: 找到 PAID 订单")
    void findPaidOrder_returnsPaidOrder() {
        insertOrder(7L, 1L, "PAID", "ORD-" + UUID.randomUUID().toString().substring(0, 8));

        Order order = orderRepository.findPaidOrder(7L, 1L);
        assertNotNull(order);
        assertEquals("PAID", order.getStatus());
    }

    @Test
    @DisplayName("findPaidOrder: 非 PAID 状态不返回")
    void findPaidOrder_notPaid_returnsNull() {
        insertOrder(7L, 1L, "PENDING", "ORD-" + UUID.randomUUID().toString().substring(0, 8));

        Order order = orderRepository.findPaidOrder(7L, 1L);
        assertNull(order, "PENDING 订单不应被 findPaidOrder 返回");
    }

    @Test
    @DisplayName("findPaidOrder: 无订单返回 null")
    void findPaidOrder_noOrder_returnsNull() {
        Order order = orderRepository.findPaidOrder(999L, 999L);
        assertNull(order);
    }

    // ==================== findPaidOrdersByCourse ====================

    @Test
    @DisplayName("findPaidOrdersByCourse: 返回直购 PAID 订单")
    void findPaidOrdersByCourse_directPurchase() {
        insertOrder(7L, 1L, "PAID", "ORD-" + UUID.randomUUID().toString().substring(0, 8));

        List<Order> orders = orderRepository.findPaidOrdersByCourse(7L, 1L);
        assertFalse(orders.isEmpty());
        assertTrue(orders.stream().allMatch(o -> "PAID".equals(o.getStatus())));
    }

    @Test
    @DisplayName("findPaidOrdersByCourse: 非 PAID 订单不被返回")
    void findPaidOrdersByCourse_notPaid_returnsEmpty() {
        insertOrder(7L, 1L, "CANCELLED", "ORD-" + UUID.randomUUID().toString().substring(0, 8));

        List<Order> orders = orderRepository.findPaidOrdersByCourse(7L, 1L);
        assertTrue(orders.isEmpty());
    }

    @Test
    @DisplayName("findPaidOrdersByCourse: 无订单返回空列表")
    void findPaidOrdersByCourse_noOrders_returnsEmpty() {
        List<Order> orders = orderRepository.findPaidOrdersByCourse(999L, 999L);
        assertNotNull(orders);
        assertTrue(orders.isEmpty());
    }

    // ==================== helper ====================

    private void insertOrder(Long userId, Long courseId, String status, String orderNo) {
        Order order = new Order();
        order.setUserId(userId);
        order.setCourseId(courseId);
        order.setStatus(status);
        order.setOrderNo(orderNo);
        order.setAmount(new BigDecimal("99.00"));
        order.setVersion(0);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        if ("PAID".equals(status)) {
            order.setPaidAt(LocalDateTime.now());
        }
        orderRepository.insert(order);
    }
}

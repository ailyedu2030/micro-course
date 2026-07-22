package com.microcourse.service;

import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.Order;
import com.microcourse.service.impl.OrderServiceImpl;
import com.microcourse.util.SecurityUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程套餐购买价格计算验证 —— 关键 P0 回归测试
 *
 * <p>背景：早期 bug 误用 course.price 为套餐购买开单，导致学生被多收/少收。
 * 修复：bundleId != null 时，必须使用 bundle.price；bundleId == null 时，使用 courseService.getMyPricing(courseId)。
 * 此外需补充大量套餐/课程校验逻辑，本测试聚焦于价格计算分支。</p>
 */
@SuppressWarnings("unchecked")
@DisplayName("课程套餐购买价格计算")
class OrderServiceBundlePriceTest {

    @Mock private com.microcourse.repository.CourseRepository courseRepository;
    @Mock private com.microcourse.repository.CourseBundleRepository bundleRepository;
    @Mock private com.microcourse.repository.CourseBundleItemRepository bundleItemRepository;
    @Mock private com.microcourse.repository.OrderRepository orderRepository;
    @Mock private com.microcourse.repository.PaymentRepository paymentRepository;
    @Mock private com.microcourse.repository.EnrollmentRepository enrollmentRepository;
    @Mock private com.microcourse.service.EnrollmentService enrollmentService;
    @Mock private com.microcourse.service.CourseService courseService;
    @InjectMocks private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MybatisPlusTestHelper.initTableInfo();
    }

    @Nested
    @DisplayName("套餐购买定价")
    class BundlePricing {

        @Test
        @DisplayName("付费套餐：使用 bundle.price，不使用 course.getMyPricing")
        void paidBundle_UsesBundlePrice() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2); // PUBLISHED
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(1); // PUBLISHED
            bundle.setPrice(new BigDecimal("199.00")); // bundle price = ¥199
            bundle.setIsFree(false);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);

            // 必修课存在 + 课程属于套餐
            when(bundleItemRepository.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1L);

            // 单课程定价服务应当不被调用 (因为 bundleId != null)
            // 这里通过后续校验订单金额

            // 模拟幂等性检查：没有现有订单
            when(orderRepository.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
            // 没有已选课
            when(enrollmentService.getMyEnrollments(anyLong(), any())).thenReturn(Collections.emptyList());

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                orderService.createOrder(1L, 100L, 50L);

                // 关键断言：插入订单的金额是 bundle.price，不是课程默认价
                org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
                org.mockito.Mockito.verify(orderRepository).insert(captor.capture());
                assertEquals(new BigDecimal("199.00"), captor.getValue().getAmount(),
                        "套餐购买必须用 bundle.price，而不是 course.getMyPricing");
                assertEquals(50L, captor.getValue().getBundleId(),
                        "订单应保存 bundle_id");
                assertEquals(100L, captor.getValue().getCourseId(),
                        "订单应保存入口课程 course_id");
                assertEquals("PENDING", captor.getValue().getStatus(),
                        "付费套餐应创建 PENDING 订单");
            }
        }

        @Test
        @DisplayName("免费套餐：直接 PAID，不调课程定价")
        void freeBundle_BypassesCoursePricing() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(1);
            bundle.setPrice(BigDecimal.ZERO);
            bundle.setIsFree(true);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);
            when(bundleItemRepository.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1L);

            // enrollBundleCourses 会查必修课 items，给出返回
            com.microcourse.entity.CourseBundleItem item = new com.microcourse.entity.CourseBundleItem();
            item.setId(1L);
            item.setBundleId(50L);
            item.setCourseId(100L);
            item.setIsRequired(true);
            when(bundleItemRepository.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(Collections.singletonList(item));

            // autoEnroll 走 enrollmentService.enroll —— stub 成功路径避免 NPE
            when(enrollmentService.enroll(any(com.microcourse.dto.EnrollmentCreateRequest.class)))
                    .thenReturn(new com.microcourse.dto.EnrollmentVO());
            // bundleId=null 调路径若被走会调 enroll, 此处 stub 一个空响应
            when(enrollmentService.getMyEnrollments(anyLong(), any())).thenReturn(Collections.emptyList());
            when(orderRepository.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                orderService.createOrder(1L, 100L, 50L);

                org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
                org.mockito.Mockito.verify(orderRepository).insert(captor.capture());
                assertEquals(0, captor.getValue().getAmount().compareTo(BigDecimal.ZERO),
                        "免费套餐订单金额应为 0");
                assertEquals("PAID", captor.getValue().getStatus(),
                        "免费套餐应直接 PAID 状态（免费购买）");
            }
        }

        @Test
        @DisplayName("免费套餐：购买后应为必修与选修都创建 enrollment")
        void freeBundle_EnrollsRequiredAndElectiveCourses() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(1);
            bundle.setPrice(BigDecimal.ZERO);
            bundle.setIsFree(true);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);
            when(bundleItemRepository.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1L);

            com.microcourse.entity.CourseBundleItem requiredItem = new com.microcourse.entity.CourseBundleItem();
            requiredItem.setId(1L);
            requiredItem.setBundleId(50L);
            requiredItem.setCourseId(100L);
            requiredItem.setIsRequired(true);

            com.microcourse.entity.CourseBundleItem electiveItem = new com.microcourse.entity.CourseBundleItem();
            electiveItem.setId(2L);
            electiveItem.setBundleId(50L);
            electiveItem.setCourseId(101L);
            electiveItem.setIsRequired(false);

            when(bundleItemRepository.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(Arrays.asList(requiredItem, electiveItem));
            when(enrollmentService.enroll(any(com.microcourse.dto.EnrollmentCreateRequest.class)))
                    .thenReturn(new com.microcourse.dto.EnrollmentVO());
            when(enrollmentService.getMyEnrollments(anyLong(), any())).thenReturn(Collections.emptyList());
            when(orderRepository.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                orderService.createOrder(1L, 100L, 50L);

                org.mockito.ArgumentCaptor<com.microcourse.dto.EnrollmentCreateRequest> enrollCaptor =
                        org.mockito.ArgumentCaptor.forClass(com.microcourse.dto.EnrollmentCreateRequest.class);
                org.mockito.Mockito.verify(enrollmentService, org.mockito.Mockito.times(2))
                        .enroll(enrollCaptor.capture());

                assertEquals(
                        java.util.Set.of(100L, 101L),
                        enrollCaptor.getAllValues().stream()
                                .map(com.microcourse.dto.EnrollmentCreateRequest::getCourseId)
                                .collect(java.util.stream.Collectors.toSet()),
                        "套餐购买后，必修和选修都应获得访问权限");
                org.junit.jupiter.api.Assertions.assertTrue(
                        enrollCaptor.getAllValues().stream()
                                .allMatch(req -> "BUNDLE".equals(req.getSourceChannel())),
                        "套餐课程 enrollment 的 sourceChannel 应统一为 BUNDLE");
            }
        }

        @Test
        @DisplayName("bundleId 为 null：走 courseService.getMyPricing 单课程定价")
        void singleCoursePricing() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            // 单课程定价 ¥29.9
            com.microcourse.dto.CoursePricingInfoVO pricing = new com.microcourse.dto.CoursePricingInfoVO();
            pricing.setFinalPrice(new BigDecimal("29.90"));
            pricing.setFree(false);
            when(courseService.getMyPricing(100L)).thenReturn(pricing);

            when(orderRepository.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(null);
            when(enrollmentService.getMyEnrollments(anyLong(), any())).thenReturn(Collections.emptyList());

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                orderService.createOrder(1L, 100L, null);

                org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
                org.mockito.Mockito.verify(orderRepository).insert(captor.capture());
                assertEquals(new BigDecimal("29.90"), captor.getValue().getAmount(),
                        "单课程购买应使用课程定价");
                org.mockito.Mockito.verify(bundleRepository, org.mockito.Mockito.never()).selectById(anyLong());
            }
        }
    }

    @Nested
    @DisplayName("套餐购买参数校验")
    class BundleValidation {

        @Test
        @DisplayName("套餐未上架：拒绝")
        void unpublishedBundle_Rejected() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(0); // DRAFT
            bundle.setPrice(new BigDecimal("99.00"));
            bundle.setIsFree(false);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                org.junit.jupiter.api.Assertions.assertThrows(
                        com.microcourse.exception.BusinessException.class,
                        () -> orderService.createOrder(1L, 100L, 50L));
            }
        }

        @Test
        @DisplayName("课程不属于套餐：拒绝（防 IDOR 替换入口）")
        void courseNotInBundle_Rejected() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(1);
            bundle.setPrice(new BigDecimal("99.00"));
            bundle.setIsFree(false);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);

            // 0 条 items 表示 course 100 不在 bundle 50 中
            when(bundleItemRepository.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                org.junit.jupiter.api.Assertions.assertThrows(
                        com.microcourse.exception.BusinessException.class,
                        () -> orderService.createOrder(1L, 100L, 50L));
            }
        }

        @Test
        @DisplayName("套餐无必修课：拒绝（防空套销售）")
        void noRequiredCourse_Rejected() {
            Course course = new Course();
            course.setId(100L);
            course.setStatus(2);
            course.setPricingStatus("APPROVED");
            when(courseRepository.selectById(100L)).thenReturn(course);

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setStatus(1);
            bundle.setPrice(new BigDecimal("99.00"));
            bundle.setIsFree(false);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);

            // selectCount 调用两次：先查 (课程是否在套餐) → 1，再查 (是否有必修课) → 0
            when(bundleItemRepository.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(1L)  // 课程属于套餐
                    .thenReturn(0L); // 没有必修课

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                org.junit.jupiter.api.Assertions.assertThrows(
                        com.microcourse.exception.BusinessException.class,
                        () -> orderService.createOrder(1L, 100L, 50L));
            }
        }
    }

    @Nested
    @DisplayName("订单展示口径")
    class OrderDisplayTitle {

        @Test
        @DisplayName("套餐订单列表应返回套餐标题而不是入口课程标题")
        void bundleOrder_UsesBundleTitleInOrderList() {
            Order order = new Order();
            order.setId(1L);
            order.setOrderNo("ORD-1");
            order.setUserId(1L);
            order.setCourseId(100L);
            order.setBundleId(50L);
            order.setAmount(new BigDecimal("199.00"));
            order.setStatus("PAID");

            Page<Order> page = new Page<>(1, 20);
            page.setRecords(List.of(order));
            page.setTotal(1);

            Course course = new Course();
            course.setId(100L);
            course.setTitle("入口课程");

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setTitle("Java 全栈套餐");

            when(orderRepository.selectPage(any(Page.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(page);
            when(courseRepository.selectBatchIds(any())).thenReturn(List.of(course));
            when(bundleRepository.selectBatchIds(any())).thenReturn(List.of(bundle));

            var result = orderService.getMyOrders(1L, 0, 20, null, null);

            assertEquals(1, result.getItems().size());
            assertEquals("Java 全栈套餐", result.getItems().get(0).getCourseTitle(),
                    "套餐订单在列表中应展示套餐标题");
            verify(bundleRepository).selectBatchIds(any());
        }
    }

    @Nested
    @DisplayName("套餐退款一致性")
    class BundleRefundConsistency {

        @Test
        @DisplayName("套餐退选失败时，不得先把订单标记为已退款")
        void bundleRefund_FailsWhenAnyEnrollmentCancelFails() {
            Order order = new Order();
            order.setId(1L);
            order.setUserId(1L);
            order.setBundleId(50L);
            order.setStatus("PAID");
            order.setAmount(new BigDecimal("199.00"));

            com.microcourse.entity.Enrollment e1 = new com.microcourse.entity.Enrollment();
            e1.setId(11L);
            e1.setUserId(1L);
            e1.setBundleId(50L);
            e1.setCourseId(100L);
            e1.setEnrollmentStatus("APPROVED");
            e1.setProgress(0.0);

            com.microcourse.entity.Enrollment e2 = new com.microcourse.entity.Enrollment();
            e2.setId(12L);
            e2.setUserId(1L);
            e2.setBundleId(50L);
            e2.setCourseId(101L);
            e2.setEnrollmentStatus("APPROVED");
            e2.setProgress(0.0);

            when(orderRepository.selectById(1L)).thenReturn(order);
            when(enrollmentRepository.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(java.util.List.<com.microcourse.entity.Enrollment>of(e1, e2),
                            java.util.List.<com.microcourse.entity.Enrollment>of(e1, e2));
            org.mockito.Mockito.doNothing().when(enrollmentService).cancelEnrollment(11L, 1L);
            org.mockito.Mockito.doThrow(new com.microcourse.exception.BusinessException(
                    com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM, "第二门课程退选失败"))
                    .when(enrollmentService).cancelEnrollment(12L, 1L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(1L)).thenReturn(true);

                assertThrows(com.microcourse.exception.BusinessException.class,
                        () -> orderService.refund(1L));
            }

            verify(orderRepository, never()).updateById(any(Order.class));
            verify(paymentRepository, never()).insert(any(com.microcourse.entity.Payment.class));
            verify(bundleRepository, never()).atomicDecrementStudentCount(anyLong());
        }

        @Test
        @DisplayName("套餐退款必须先回收访问权，再写退款状态和流水")
        void bundleRefund_RevokesAccessBeforeMarkingRefunded() {
            Order order = new Order();
            order.setId(1L);
            order.setUserId(1L);
            order.setBundleId(50L);
            order.setStatus("PAID");
            order.setAmount(new BigDecimal("199.00"));
            order.setPaymentMethod("MOCK");

            CourseBundle bundle = new CourseBundle();
            bundle.setId(50L);
            bundle.setTitle("Java 全栈套餐");

            com.microcourse.entity.Enrollment e1 = new com.microcourse.entity.Enrollment();
            e1.setId(11L);
            e1.setUserId(1L);
            e1.setBundleId(50L);
            e1.setCourseId(100L);
            e1.setEnrollmentStatus("APPROVED");
            e1.setProgress(0.0);

            com.microcourse.entity.Enrollment e2 = new com.microcourse.entity.Enrollment();
            e2.setId(12L);
            e2.setUserId(1L);
            e2.setBundleId(50L);
            e2.setCourseId(101L);
            e2.setEnrollmentStatus("APPROVED");
            e2.setProgress(0.0);

            when(orderRepository.selectById(1L)).thenReturn(order, order);
            when(enrollmentRepository.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                    .thenReturn(java.util.List.<com.microcourse.entity.Enrollment>of(e1, e2),
                            java.util.List.<com.microcourse.entity.Enrollment>of(e1, e2));
            when(orderRepository.updateById(any(Order.class))).thenReturn(1);
            when(bundleRepository.selectById(50L)).thenReturn(bundle);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(1L)).thenReturn(true);

                orderService.refund(1L);
            }

            org.mockito.InOrder inOrder = inOrder(enrollmentService, orderRepository, paymentRepository, bundleRepository);
            inOrder.verify(enrollmentService).cancelEnrollment(11L, 1L);
            inOrder.verify(enrollmentService).cancelEnrollment(12L, 1L);
            inOrder.verify(bundleRepository).atomicDecrementStudentCount(50L);
            inOrder.verify(orderRepository).updateById(any(Order.class));
            inOrder.verify(paymentRepository).insert(any(com.microcourse.entity.Payment.class));
        }
    }
}

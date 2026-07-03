package com.microcourse.service;

import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.Order;
import com.microcourse.service.impl.OrderServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 课程套餐购买价格计算验证 —— 关键 P0 回归测试
 *
 * <p>背景：早期 bug 误用 course.price 为套餐购买开单，导致学生被多收/少收。
 * 修复：bundleId != null 时，必须使用 bundle.price；bundleId == null 时，使用 courseService.getMyPricing(courseId)。
 * 此外需补充大量套餐/课程校验逻辑，本测试聚焦于价格计算分支。</p>
 */
@DisplayName("课程套餐购买价格计算")
class OrderServiceBundlePriceTest {

    @Mock private com.microcourse.repository.CourseRepository courseRepository;
    @Mock private com.microcourse.repository.CourseBundleRepository bundleRepository;
    @Mock private com.microcourse.repository.CourseBundleItemRepository bundleItemRepository;
    @Mock private com.microcourse.repository.OrderRepository orderRepository;
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
}

package com.microcourse.service;

import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseFavorite;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.repository.CourseFavoriteRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.CourseAdminServiceImpl;
import com.microcourse.service.impl.CourseFavoriteServiceImpl;
import com.microcourse.service.impl.ExerciseRecordServiceImpl;
import com.microcourse.service.impl.NotificationServiceImpl;
import com.microcourse.service.impl.OrderPaymentServiceImpl;
import com.microcourse.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全页面审查批次关键修复的回归测试（F-2026-08-05 系列）。
 * 覆盖：软删收藏恢复 / 管理员建课教师校验 / 通知分类映射完整性 /
 * 考试 attempt 汇总 / 免费订单先建单后选课。
 */
class AuditRegressionFixesTest {

    private com.microcourse.repository.CourseCategoryRepository categoryRepo;

    // ============ 1. 收藏：软删后重新收藏恢复原行而非 insert（原 409） ============
    @Test
    void favoriteRestoresSoftDeletedRowInsteadOfInsert() {
        CourseFavoriteRepository favoriteRepository = mock(CourseFavoriteRepository.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CourseFavoriteServiceImpl service =
                new CourseFavoriteServiceImpl(favoriteRepository, courseRepository, userRepository);

        Course course = new Course();
        course.setId(1L);
        when(courseRepository.selectById(1L)).thenReturn(course);
        // 活跃记录数 0（逻辑删除被过滤），但存在软删行可恢复
        when(favoriteRepository.selectCount(any())).thenReturn(0L);
        when(favoriteRepository.restoreByUserAndCourse(5L, 1L)).thenReturn(1);

        Map<String, Object> result = service.favorite(5L, 1L);

        assertFalse((Boolean) result.get("alreadyFavorited"));
        verify(favoriteRepository).restoreByUserAndCourse(5L, 1L);
        verify(favoriteRepository, never()).insert(any(CourseFavorite.class));
    }

    // ============ 2. 管理员建课未指定授课教师 → 明确报错（原 teacher_id NOT NULL 409） ============
    @Test
    void adminCreateCourseWithoutTeacherRejected() {
        CourseAdminServiceImpl service = buildCourseAdminService();
        when(categoryRepo.selectById(1L)).thenReturn(new com.microcourse.entity.CourseCategory());
        CourseCreateRequest request = new CourseCreateRequest();
        request.setTitle("无教师课程");
        request.setCategoryId(1L);
        request.setTeacherId(null);

        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(() -> SecurityUtil.hasRole("TEACHER")).thenReturn(false);
            su.when(SecurityUtil::isAdmin).thenReturn(true);
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
            assertTrue(ex.getMessage().contains("指定授课教师"));
        }
    }

    // ============ 3. 通知分类映射覆盖全部 NotificationType 码（筛选恒空态根因） ============
    @Test
    void notificationTypeCategoryCoversAllCodes() throws Exception {
        Field field = NotificationServiceImpl.class.getDeclaredField("TYPE_CATEGORY");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> category = (Map<String, List<String>>) field.get(null);

        for (NotificationType type : NotificationType.values()) {
            boolean covered = category.values().stream().anyMatch(codes -> codes.contains(type.getCode()));
            assertTrue(covered, "通知类型 " + type.getCode() + " 未进入任何筛选分类（分类筛选将看不到该通知）");
        }
    }

    // ============ 4. 考试 attempt 汇总：passed/score/attemptCount（"已完成"tab 根因） ============
    @Test
    void attemptSummaryReturnsPassedAndLatestScore() {
        ExerciseRecordRepository repo = mock(ExerciseRecordRepository.class);
        ExerciseRecordServiceImpl service = buildExerciseRecordService(repo);

        ExerciseRecord latest = new ExerciseRecord();
        latest.setUserId(5L);
        latest.setExerciseId(9L);
        latest.setAttemptNo(2);
        latest.setScore(100);
        latest.setPassed(true);
        latest.setSubmittedAt(LocalDateTime.now());
        ExerciseRecord earlier = new ExerciseRecord();
        earlier.setUserId(5L);
        earlier.setExerciseId(9L);
        earlier.setAttemptNo(1);
        earlier.setScore(40);
        earlier.setPassed(false);
        earlier.setSubmittedAt(LocalDateTime.now().minusDays(1));
        when(repo.selectList(any())).thenReturn(List.of(earlier, latest));

        Map<String, Object> summary = service.getAttemptSummary(5L, 9L);
        assertEquals(2, summary.get("attemptCount"));
        assertEquals(Boolean.TRUE, summary.get("passed"));
        assertEquals(100, summary.get("score"));
    }

    // ============ 5. 免费订单：先落 PAID 订单再自动选课（原 9005 非法支付来源） ============
    @Test
    void freeOrderInsertsPaidOrderBeforeAutoEnroll() {
        com.microcourse.repository.OrderRepository orderRepository =
                mock(com.microcourse.repository.OrderRepository.class);
        com.microcourse.repository.CourseBundleRepository bundleRepository =
                mock(com.microcourse.repository.CourseBundleRepository.class);
        com.microcourse.repository.CourseBundleItemRepository bundleItemRepository =
                mock(com.microcourse.repository.CourseBundleItemRepository.class);
        com.microcourse.repository.EnrollmentRepository enrollmentRepository =
                mock(com.microcourse.repository.EnrollmentRepository.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseService courseService = mock(CourseService.class);
        EnrollmentService enrollmentService = mock(EnrollmentService.class);
        OrderQueryService orderQueryService = mock(OrderQueryService.class);
        com.microcourse.util.RedisUtil redisUtil = mock(com.microcourse.util.RedisUtil.class);
        com.microcourse.payment.PaymentSignatureValidator validator =
                mock(com.microcourse.payment.PaymentSignatureValidator.class);

        OrderPaymentServiceImpl service = new OrderPaymentServiceImpl(
                orderRepository, mock(com.microcourse.repository.PaymentRepository.class),
                courseRepository, bundleRepository, bundleItemRepository,
                enrollmentRepository, courseService, enrollmentService,
                orderQueryService, redisUtil, validator);

        Course course = new Course();
        course.setId(1L);
        course.setStatus(CourseStatus.APPROVED.getCode());
        course.setPricingStatus("APPROVED");
        when(courseRepository.selectById(1L)).thenReturn(course);
        com.microcourse.dto.CoursePricingInfoVO pricing = new com.microcourse.dto.CoursePricingInfoVO();
        pricing.setFree(true);
        pricing.setFinalPrice(java.math.BigDecimal.ZERO);
        when(courseService.getMyPricing(1L)).thenReturn(pricing);
        when(orderRepository.insert(any())).thenReturn(1);

        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(5L);
            service.createOrder(5L, 1L, null);
        }

        InOrder inOrder = inOrder(orderRepository, enrollmentService);
        inOrder.verify(orderRepository).insert(any());
        inOrder.verify(enrollmentService).enroll(any());
    }

    private CourseAdminServiceImpl buildCourseAdminService() {
        categoryRepo = mock(com.microcourse.repository.CourseCategoryRepository.class);
        return new CourseAdminServiceImpl(
                mock(CourseRepository.class),
                categoryRepo,
                mock(com.microcourse.repository.CourseChapterRepository.class),
                mock(com.microcourse.repository.VideoRepository.class),
                mock(UserRepository.class),
                mock(com.microcourse.repository.CourseReviewRepository.class),
                mock(com.microcourse.repository.EnrollmentRepository.class),
                mock(com.microcourse.repository.PluginGrantRepository.class),
                mock(com.microcourse.repository.LearningProgressRepository.class),
                mock(com.microcourse.repository.DiscussionCommentRepository.class),
                mock(com.microcourse.repository.CourseNoteRepository.class),
                mock(com.microcourse.repository.VideoBookmarkRepository.class),
                mock(com.microcourse.repository.ExerciseRepository.class),
                mock(com.microcourse.repository.DiscussionPostRepository.class),
                mock(com.microcourse.plugin.interactive.mapper.CourseSlideMapper.class),
                mock(com.microcourse.plugin.interactive.mapper.SlidePageMapper.class),
                mock(CourseAuditService.class),
                mock(CourseStateMachine.class),
                mock(CourseCopyContentService.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(com.microcourse.event.DomainEventPublisher.class),
                mock(com.microcourse.repository.HermesCourseMappingRepository.class),
                mock(com.microcourse.service.CourseTypeChangeValidator.class));
    }

    private static ExerciseRecordServiceImpl buildExerciseRecordService(ExerciseRecordRepository repo) {
        return new ExerciseRecordServiceImpl(
                repo,
                mock(com.microcourse.repository.ExerciseRepository.class),
                mock(com.microcourse.repository.ExerciseQuestionRepository.class),
                mock(com.microcourse.repository.QuestionRepository.class),
                mock(com.microcourse.repository.WrongQuestionRepository.class),
                mock(com.microcourse.repository.GradeRepository.class),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                mock(CourseRepository.class),
                mock(com.microcourse.repository.EnrollmentRepository.class),
                mock(NotificationService.class),
                mock(com.microcourse.repository.VideoRepository.class),
                mock(com.microcourse.repository.LearningProgressRepository.class),
                mock(org.springframework.data.redis.core.StringRedisTemplate.class));
    }
}

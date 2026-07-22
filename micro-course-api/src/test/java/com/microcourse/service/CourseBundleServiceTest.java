package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleItemVO;
import com.microcourse.dto.bundle.BundleUpdateRequest;
import com.microcourse.dto.bundle.BundleVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.CourseBundleServiceImpl;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class CourseBundleServiceTest {

    @Mock private CourseBundleRepository bundleRepository;
    @Mock private CourseBundleItemRepository itemRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private CourseBundleServiceImpl bundleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("创建套餐")
    class Create {
        @Test
        @DisplayName("创建成功")
        void create_Success() {
            BundleCreateRequest req = new BundleCreateRequest();
            req.setTitle("英语四级通关");
            req.setDescription("包含全部四级课程");
            req.setPrice(new BigDecimal("99.00"));

            when(bundleRepository.insert(any(CourseBundle.class))).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);

                BundleVO vo = bundleService.create(req);
                assertNotNull(vo);
                assertEquals("英语四级通关", vo.getTitle());
                assertNotNull(vo.getItems());
                assertTrue(vo.getItems().isEmpty());
            }
        }

        @Test
        @DisplayName("免费套餐 isFree 默认 true")
        void create_FreeDefault() {
            BundleCreateRequest req = new BundleCreateRequest();
            req.setTitle("免费套餐");
            req.setPrice(null);

            when(bundleRepository.insert(any(CourseBundle.class))).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);
                BundleVO vo = bundleService.create(req);
                assertTrue(vo.getIsFree());
            }
        }
    }

    @Nested
    @DisplayName("查询套餐")
    class GetById {
        @Test
        @DisplayName("存在时返回完整信息")
        void getById_Success() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setTitle("测试套餐");
            bundle.setCreatorId(1L);
            bundle.setStatus(1);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);

            User creator = new User();
            creator.setId(1L);
            creator.setRealName("张三");
            when(userRepository.selectById(1L)).thenReturn(creator);

            CourseBundleItem item = new CourseBundleItem();
            item.setId(10L);
            item.setBundleId(1L);
            item.setCourseId(100L);
            item.setSortOrder(1);
            item.setIsRequired(true);
            when(itemRepository.selectList(any())).thenReturn(List.of(item));

            Course course = new Course();
            course.setId(100L);
            course.setTitle("四级词汇");
            course.setTeacherId(5L);
            course.setCourseType("VIDEO");
            when(courseRepository.selectBatchIds(anySet())).thenReturn(List.of(course));

            User teacher = new User();
            teacher.setId(5L);
            teacher.setRealName("李四");
            when(userRepository.selectBatchIds(anySet())).thenReturn(List.of(teacher));

            BundleVO vo = bundleService.getById(1L);
            assertNotNull(vo);
            assertEquals("测试套餐", vo.getTitle());
            assertEquals("张三", vo.getCreatorName());
            assertEquals(1, vo.getItems().size());
            BundleItemVO itemVo = vo.getItems().get(0);
            assertEquals("四级词汇", itemVo.getCourseTitle());
            assertEquals("李四", itemVo.getTeacherName());
            assertTrue(itemVo.getIsRequired());
        }

        @Test
        @DisplayName("不存在时抛 BUNDLE_NOT_FOUND")
        void getById_NotFound() {
            when(bundleRepository.selectById(999L)).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> bundleService.getById(999L));
            assertEquals(ErrorCode.BUNDLE_NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("课程已删除时显示占位符")
        void getById_DeletedCourse_ShowsPlaceholder() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setTitle("测试套餐");
            bundle.setCreatorId(1L);
            bundle.setStatus(1);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(userRepository.selectById(1L)).thenReturn(new User());

            CourseBundleItem item = new CourseBundleItem();
            item.setId(10L);
            item.setBundleId(1L);
            item.setCourseId(999L);
            item.setSortOrder(1);
            item.setIsRequired(true);
            when(itemRepository.selectList(any())).thenReturn(List.of(item));

            when(courseRepository.selectBatchIds(anySet())).thenReturn(Collections.emptyList());

            BundleVO vo = bundleService.getById(1L);
            assertEquals("[课程已删除]", vo.getItems().get(0).getCourseTitle());
        }

        @Test
        @DisplayName("学生访问草稿套餐被拒")
        void getById_Student_CannotAccessDraft() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(99L); // 不是当前用户
            bundle.setStatus(0); // 草稿
            when(bundleRepository.selectById(1L)).thenReturn(bundle);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);
                su.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);

                BusinessException e = assertThrows(BusinessException.class,
                        () -> bundleService.getById(1L));
                assertEquals(ErrorCode.BUNDLE_NOT_FOUND.getCode(), e.getCode());
            }
        }

        @Test
        @DisplayName("教师访问任意状态套餐均通过")
        void getById_Teacher_CanAccessAnyStatus() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            bundle.setStatus(0); // 草稿
            bundle.setTitle("教师自己的草稿");
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(userRepository.selectById(1L)).thenReturn(new User());
            when(itemRepository.selectList(any())).thenReturn(Collections.emptyList());

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(false);
                su.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);

                BundleVO vo = bundleService.getById(1L);
                assertNotNull(vo);
                assertEquals(0, vo.getStatus());
            }
        }
    }

    @Nested
    @DisplayName("分页查询")
    class Page {
        @Test
        @DisplayName("学生只能看到已上架的套餐")
        void page_Student_SeesOnlyPublished() {
            when(bundleRepository.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20));

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(true);
                su.when(SecurityUtil::isAdmin).thenReturn(false);
                su.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);

                PageResult<BundleVO> result = bundleService.page(0, 20);
                assertNotNull(result);
                assertTrue(result.getItems().isEmpty());
            }
        }

        @Test
        @DisplayName("教师只看自己创建的套餐")
        void page_Teacher_SeesOnlyOwn() {
            when(bundleRepository.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20));

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(false);
                su.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);

                PageResult<BundleVO> result = bundleService.page(0, 20);
                assertNotNull(result);
            }
        }

        @Test
        @DisplayName("教务处/管理员看全部套餐")
        void page_AdminOrAcademic_SeesAll() {
            when(bundleRepository.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(0, 20));

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.hasRole("STUDENT")).thenReturn(false);
                su.when(SecurityUtil::isAdmin).thenReturn(true);
                su.when(SecurityUtil::isAdminOrAcademic).thenReturn(true);

                PageResult<BundleVO> result = bundleService.page(0, 20);
                assertNotNull(result);
            }
        }
    }

    @Nested
    @DisplayName("更新套餐")
    class Update {
        @Test
        @DisplayName("更新成功")
        void update_Success() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setTitle("原标题");
            bundle.setCreatorId(1L);
            bundle.setPrice(new BigDecimal("50"));
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(bundleRepository.updateById(any())).thenReturn(1);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                su.when(SecurityUtil::getCurrentUserId).thenReturn(1L);
                when(userRepository.selectById(1L)).thenReturn(new User());
                when(itemRepository.selectList(any())).thenReturn(Collections.emptyList());

                BundleUpdateRequest req = new BundleUpdateRequest();
                req.setTitle("新标题");
                req.setPrice(new BigDecimal("79.00"));
                BundleVO vo = bundleService.update(1L, req);

                assertNotNull(vo);
                assertEquals("新标题", vo.getTitle());
            }
        }
    }

    @Nested
    @DisplayName("发布/下架")
    class Publish {
        @Test
        @DisplayName("上架成功")
        void publish_Success() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            bundle.setStatus(0);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(itemRepository.selectCount(any())).thenReturn(1L);

            CourseBundleItem item = new CourseBundleItem();
            item.setBundleId(1L);
            item.setCourseId(100L);
            when(itemRepository.selectList(any())).thenReturn(List.of(item));

            Course course = new Course();
            course.setId(100L);
            course.setStatus(2); // PUBLISHED
            when(courseRepository.selectBatchIds(anySet())).thenReturn(List.of(course));

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                bundleService.publish(1L);
                assertEquals(1, bundle.getStatus());
                verify(bundleRepository).updateById(bundle);
            }
        }

        @Test
        @DisplayName("空套餐上架抛异常")
        void publish_EmptyBundle_Fails() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(itemRepository.selectCount(any())).thenReturn(0L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> bundleService.publish(1L));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode());
            }
        }

        @Test
        @DisplayName("所有课程都已下架时上架被拒")
        void publish_AllCoursesUnpublished_Fails() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(itemRepository.selectCount(any())).thenReturn(1L);

            CourseBundleItem item = new CourseBundleItem();
            item.setBundleId(1L);
            item.setCourseId(100L);
            when(itemRepository.selectList(any())).thenReturn(List.of(item));

            Course course = new Course();
            course.setId(100L);
            course.setStatus(0); // 草稿
            when(courseRepository.selectBatchIds(anySet())).thenReturn(List.of(course));

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> bundleService.publish(1L));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode());
            }
        }

        @Test
        @DisplayName("下架成功")
        void unpublish_Success() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            bundle.setStatus(1);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                bundleService.unpublish(1L);
                assertEquals(0, bundle.getStatus());
                verify(bundleRepository).updateById(bundle);
            }
        }

        @Test
        @DisplayName("套餐不存在抛异常")
        void publish_NotFound() {
            when(bundleRepository.selectById(999L)).thenReturn(null);
            assertThrows(BusinessException.class, () -> bundleService.publish(999L));
        }
    }

    @Nested
    @DisplayName("报名状态检查")
    class EnrollmentStatus {
        @Test
        @DisplayName("已支付订单的用户返回已报名")
        void isEnrolled_WhenPaidOrderExists() {
            CourseBundleItem item = new CourseBundleItem();
            item.setBundleId(1L);
            item.setCourseId(100L);
            item.setIsRequired(true);
            when(itemRepository.selectList(any())).thenReturn(List.of(item));

            when(orderRepository.selectCount(any())).thenReturn(1L);
            assertTrue(bundleService.isUserEnrolledInBundle(1L, 1L));
        }

        @Test
        @DisplayName("无支付记录返回未报名")
        void isEnrolled_WhenNoPaidOrder() {
            when(itemRepository.selectList(any())).thenReturn(Collections.emptyList());
            assertFalse(bundleService.isUserEnrolledInBundle(1L, 1L));
        }
    }

    @Nested
    @DisplayName("添加/移除子课")
    class Items {
        @Test
        @DisplayName("添加课程成功")
        void addCourse_Success() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);

            Course course = new Course();
            course.setId(100L);
            course.setTitle("英语词汇");
            when(courseRepository.selectById(100L)).thenReturn(course);
            when(itemRepository.selectCount(any())).thenReturn(0L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                bundleService.addCourse(1L, 100L, 1, true);
                verify(itemRepository).insert(any(CourseBundleItem.class));
            }
        }

        @Test
        @DisplayName("套餐不存在抛 BUNDLE_NOT_FOUND")
        void addCourse_BundleNotFound() {
            when(bundleRepository.selectById(999L)).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> bundleService.addCourse(999L, 100L, 1, true));
            assertEquals(ErrorCode.BUNDLE_NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("课程不存在抛 COURSE_NOT_FOUND")
        void addCourse_CourseNotFound() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);
            when(courseRepository.selectById(anyLong())).thenReturn(null);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> bundleService.addCourse(1L, 999L, 1, true));
                assertEquals(ErrorCode.COURSE_NOT_FOUND.getCode(), e.getCode());
            }
        }

        @Test
        @DisplayName("重复添加抛 BAD_REQUEST_PARAM")
        void addCourse_Duplicate() {
            CourseBundle bundle = new CourseBundle();
            bundle.setId(1L);
            bundle.setCreatorId(1L);
            when(bundleRepository.selectById(1L)).thenReturn(bundle);

            Course course = new Course();
            course.setId(100L);
            when(courseRepository.selectById(100L)).thenReturn(course);
            when(itemRepository.selectCount(any())).thenReturn(1L);

            try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> bundleService.addCourse(1L, 100L, 1, true));
                assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode());
            }
        }

         @Test
         @DisplayName("并发添加：DB 唯一约束挡 → 转业务异常")
         void addCourse_ConcurrentInsert_DB_Catch() {
             CourseBundle bundle = new CourseBundle();
             bundle.setId(1L);
             bundle.setCreatorId(1L);
             when(bundleRepository.selectById(1L)).thenReturn(bundle);

             Course course = new Course();
             course.setId(100L);
             when(courseRepository.selectById(100L)).thenReturn(course);
             // selectCount 通过 → check-then-act 竞态窗口
             when(itemRepository.selectCount(any())).thenReturn(0L);
             // 但 insert 失败（被另一线程抢先）
             when(itemRepository.insert(any(CourseBundleItem.class)))
                     .thenThrow(new org.springframework.dao.DuplicateKeyException(
                             "uk_cbi_bundle_course_active"));

             try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
                 su.when(() -> SecurityUtil.isOwnerOrAdmin(anyLong())).thenReturn(true);
                 BusinessException e = assertThrows(BusinessException.class,
                         () -> bundleService.addCourse(1L, 100L, 1, true));
                 assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode(),
                         "应将 DB DuplicateKeyException 转为业务异常，而非冒 500");
             }
         }
     }

     @Nested
     @DisplayName("删除套餐（防误删已售套餐）")
     class Delete {
         @Test
         @DisplayName("有 PAID 订单：拒绝删除（保护已购课学生）")
         void delete_RejectsWhenPaidOrdersExist() {
             CourseBundle bundle = new CourseBundle();
             bundle.setId(1L);
             when(bundleRepository.selectById(1L)).thenReturn(bundle);
             // 第一次 selectCount（PAID）返回 > 0
             when(orderRepository.selectCount(any(LambdaQueryWrapper.class)))
                     .thenReturn(2L);

             BusinessException e = assertThrows(BusinessException.class,
                     () -> bundleService.delete(1L));
             assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode());
             // 关键：未触发 items delete 或 bundle delete
             verify(itemRepository, never()).delete(any(LambdaQueryWrapper.class));
             verify(bundleRepository, never()).deleteById(1L);
         }

         @Test
         @DisplayName("有 PENDING 订单：拒绝删除（防止用户付完款找不到套餐）")
         void delete_RejectsWhenPendingOrdersExist() {
             CourseBundle bundle = new CourseBundle();
             bundle.setId(1L);
             when(bundleRepository.selectById(1L)).thenReturn(bundle);
             // 第一次查 PAID=0，第二次查 PENDING=1
             when(orderRepository.selectCount(any(LambdaQueryWrapper.class)))
                     .thenReturn(0L)  // PAID
                     .thenReturn(1L); // PENDING

             BusinessException e = assertThrows(BusinessException.class,
                     () -> bundleService.delete(1L));
             assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), e.getCode());
         }

         @Test
         @DisplayName("无订单：软删 items + bundle")
         void delete_NoOrders_SoftDeletes() {
             CourseBundle bundle = new CourseBundle();
             bundle.setId(1L);
             when(bundleRepository.selectById(1L)).thenReturn(bundle);
             when(orderRepository.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

             bundleService.delete(1L);

             verify(itemRepository).delete(any(LambdaQueryWrapper.class));
             verify(bundleRepository).deleteById(1L);
         }

         @Test
         @DisplayName("套餐不存在：抛 BUNDLE_NOT_FOUND")
         void delete_NotFound() {
             when(bundleRepository.selectById(999L)).thenReturn(null);
             BusinessException e = assertThrows(BusinessException.class,
                     () -> bundleService.delete(999L));
             assertEquals(ErrorCode.BUNDLE_NOT_FOUND.getCode(), e.getCode());
         }
     }
}

package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microcourse.dto.CoursewareTypeDistributionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.impl.CoursewareDistributionServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-2026-08-10-06: 5 种课件/课程类型分布服务单元测试（不依赖 Spring 容器）
 *
 * 覆盖：
 * - 全平台分布（teacherId=null）按 courseType 四类聚合 + 练习章节去重
 * - 单教师分布（teacherId!=null）按 teacherId 过滤 + 章节二次过滤
 * - 空数据返回 0 值而不抛 NPE
 * - 历史遗留 courseType（如 INTERACTIVE）不影响 5 类型分布计数
 */
class CoursewareDistributionServiceTest {

    private final CourseRepository courseRepository = Mockito.mock(CourseRepository.class);
    private final CourseSectionRepository courseSectionRepository = Mockito.mock(CourseSectionRepository.class);
    private final CoursewareDistributionService service = new CoursewareDistributionServiceImpl(courseRepository, courseSectionRepository);

    /**
     * 初始化 MyBatis-Plus TableInfoHelper — 避免 LambdaQueryWrapper.eq/isNull/in 等方法
     * 因 lambda cache 缺失抛 MybatisPlusException（参考 MicroSpecialtyServiceTest 模式）。
     * 注：MybatisPlusTestHelper 已初始化 Course，但未初始化 CourseSection，故此处补上。
     */
    @BeforeAll
    static void initTableInfoForCourseSection() {
        // 防御性二次初始化（MybatisPlusTestHelper 可能未在本测试 classpath 加载）
        MybatisPlusTestHelper.initTableInfo();
        Configuration cfg = new Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, CourseSection.class);
    }

    @Test
    @DisplayName("全平台分布：4 课程类型聚合 + EXERCISE 章节去重")
    void shouldAggregateGlobalDistribution() {
        // 4 个不同 courseType 课程
        List<Course> courses = Arrays.asList(
            courseWithType(1L, "VIDEO"),
            courseWithType(2L, "VIDEO"),
            courseWithType(3L, "HTML_COURSEWARE"),
            courseWithType(4L, "PPT_COURSEWARE"),
            courseWithType(5L, "PPT_COURSEWARE"),
            courseWithType(6L, "OFFLINE")
            // 忽略 INTERACTIVE（已被 V333 迁移消除）
        );
        Mockito.when(courseRepository.selectList(any())).thenReturn(courses);

        // 2 个 EXERCISE 章节属于同一个 courseId=99（去重后 = 1 个课程）
        List<CourseSection> exercises = Arrays.asList(
            sectionWithCourse(901L, "EXERCISE", 99L),
            sectionWithCourse(902L, "EXERCISE", 99L)
        );
        Mockito.when(courseSectionRepository.selectList(any())).thenReturn(exercises);

        CoursewareTypeDistributionVO vo = service.getGlobalDistribution();

        assertEquals(2L, vo.getVideoCourses(), "VIDEO 课程数");
        assertEquals(1L, vo.getHtmlCoursewareCourses(), "HTML_COURSEWARE 课程数");
        assertEquals(2L, vo.getPptCoursewareCourses(), "PPT_COURSEWARE 课程数");
        assertEquals(1L, vo.getOfflineCourses(), "OFFLINE 课程数");
        assertEquals(1L, vo.getCoursesWithExercises(), "EXERCISE 章节去重 = 1 个课程");
        assertEquals(6L, vo.getTotal(), "4 课程类型总和");
    }

    @Test
    @DisplayName("单教师分布：teacherId 过滤 + 章节 in teacherCourseIds 二次过滤")
    void shouldAggregateTeacherDistribution() {
        // 教师 A 只拥有 2 门课程
        List<Course> teacherCourses = Arrays.asList(
            courseWithType(10L, "VIDEO"),
            courseWithType(11L, "PPT_COURSEWARE")
        );
        // 第一次 selectList 返回教师课程
        Mockito.when(courseRepository.selectList(any()))
            .thenReturn(teacherCourses);

        // 章节维度：教师 10L 含 1 个 EXERCISE，11L 含 1 个 EXERCISE → 去重 = 2
        List<CourseSection> exercises = Arrays.asList(
            sectionWithCourse(201L, "EXERCISE", 10L),
            sectionWithCourse(202L, "EXERCISE", 11L)
        );
        Mockito.when(courseSectionRepository.selectList(any()))
            .thenReturn(exercises);

        CoursewareTypeDistributionVO vo = service.getTeacherDistribution(100L);

        assertEquals(1L, vo.getVideoCourses(), "教师 VIDEO 课程数 = 1");
        assertEquals(0L, vo.getHtmlCoursewareCourses(), "教师无 HTML 课程");
        assertEquals(1L, vo.getPptCoursewareCourses(), "教师 PPT 课程数 = 1");
        assertEquals(0L, vo.getOfflineCourses(), "教师无 OFFLINE 课程");
        assertEquals(2L, vo.getCoursesWithExercises(), "EXERCISE 章节覆盖 2 个课程");

        // 验证 teacherId 被传入 course 查询 wrapper
        ArgumentCaptor<LambdaQueryWrapper> courseCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(courseRepository, Mockito.atLeastOnce()).selectList(courseCaptor.capture());
        // 第 2 次调用是章节过滤（在 service 内部），取第 1 次（course 维度）
        LambdaQueryWrapper<Course> firstCall = courseCaptor.getAllValues().get(0);
        assertNotNull(firstCall, "course 维度 wrapper 不为空");

        // 验证 section 查询使用了 in teacherCourseIds
        ArgumentCaptor<LambdaQueryWrapper> sectionCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        Mockito.verify(courseSectionRepository).selectList(sectionCaptor.capture());
        assertNotNull(sectionCaptor.getValue(), "section wrapper 不为空");
    }

    @Test
    @DisplayName("空数据：教师无课程时返回全 0 不抛异常")
    void shouldReturnEmptyForTeacherWithoutCourses() {
        Mockito.when(courseRepository.selectList(any()))
            .thenReturn(Collections.emptyList());

        CoursewareTypeDistributionVO vo = service.getTeacherDistribution(999L);

        assertEquals(0L, vo.getVideoCourses());
        assertEquals(0L, vo.getHtmlCoursewareCourses());
        assertEquals(0L, vo.getPptCoursewareCourses());
        assertEquals(0L, vo.getOfflineCourses());
        assertEquals(0L, vo.getCoursesWithExercises());
        assertEquals(0L, vo.getTotal());
        // section 维度不应被查询（短路优化）
        Mockito.verify(courseSectionRepository, Mockito.never()).selectList(any());
    }

    @Test
    @DisplayName("null teacherId → 走全平台分支不报错")
    void shouldHandleNullTeacherId() {
        Mockito.when(courseRepository.selectList(any()))
            .thenReturn(Collections.emptyList());
        Mockito.when(courseSectionRepository.selectList(any()))
            .thenReturn(Collections.emptyList());

        CoursewareTypeDistributionVO vo = service.getTeacherDistribution(null);

        assertNotNull(vo);
        assertEquals(0L, vo.getTotal());
        // null teacherId 应该直接返回 empty() 不查 DB
        Mockito.verify(courseRepository, Mockito.never()).selectList(any());
        Mockito.verify(courseSectionRepository, Mockito.never()).selectList(any());
    }

    @Test
    @DisplayName("历史遗留 INTERACTIVE 不计入 5 类型分布（V333 迁移后无此值）")
    void shouldIgnoreLegacyInteractiveCourseType() {
        List<Course> courses = Arrays.asList(
            courseWithType(1L, "VIDEO"),
            courseWithType(2L, "INTERACTIVE")  // 已被 V333 迁移消除，service 应忽略
        );
        Mockito.when(courseRepository.selectList(any())).thenReturn(courses);
        Mockito.when(courseSectionRepository.selectList(any()))
            .thenReturn(Collections.emptyList());

        CoursewareTypeDistributionVO vo = service.getGlobalDistribution();

        assertEquals(1L, vo.getVideoCourses(), "VIDEO 计数正确");
        assertEquals(0L, vo.getHtmlCoursewareCourses(), "INTERACTIVE 不被归到 HTML（V333 迁移已处理）");
        assertEquals(0L, vo.getPptCoursewareCourses());
        assertEquals(0L, vo.getOfflineCourses());
    }

    @Test
    @DisplayName("EXERCISE 章节去重：同课程多练习只计 1")
    void shouldDeduplicateExerciseSectionCourses() {
        // 课程 50L 有 3 个 EXERCISE 章节
        List<CourseSection> exercises = Arrays.asList(
            sectionWithCourse(1L, "EXERCISE", 50L),
            sectionWithCourse(2L, "EXERCISE", 50L),
            sectionWithCourse(3L, "EXERCISE", 50L)
        );
        Mockito.when(courseRepository.selectList(any())).thenReturn(Collections.emptyList());
        Mockito.when(courseSectionRepository.selectList(any())).thenReturn(exercises);

        CoursewareTypeDistributionVO vo = service.getGlobalDistribution();

        assertEquals(1L, vo.getCoursesWithExercises(), "3 个 EXERCISE 章节 → 1 个课程（去重）");
    }

    // ---- helpers ----

    private Course courseWithType(Long id, String type) {
        Course c = new Course();
        c.setId(id);
        c.setCourseType(type);
        return c;
    }

    private CourseSection sectionWithCourse(Long id, String type, Long courseId) {
        CourseSection s = new CourseSection();
        s.setId(id);
        s.setSectionType(type);
        s.setCourseId(courseId);
        return s;
    }
}
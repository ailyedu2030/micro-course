package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CoursewareTypeDistributionVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.CoursewareDistributionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 5 种课件/课程类型分布服务实现（F-2026-08-10-06）
 *
 * 查询策略：
 * - 4 种课程维度：单条 SQL GROUP BY course_type，性能 O(1)
 * - 1 种章节维度（练习）：先按 section_type=EXERCISE 取所有 section，再内存去重 course_id
 *   课程数本身不大（千级别），去重在内存即可；后续可优化为 SQL DISTINCT。
 */
@Service
public class CoursewareDistributionServiceImpl implements CoursewareDistributionService {

    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;

    public CoursewareDistributionServiceImpl(CourseRepository courseRepository,
                                             CourseSectionRepository courseSectionRepository) {
        this.courseRepository = courseRepository;
        this.courseSectionRepository = courseSectionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CoursewareTypeDistributionVO getGlobalDistribution() {
        return buildDistribution(null);
    }

    @Override
    @Transactional(readOnly = true)
    public CoursewareTypeDistributionVO getTeacherDistribution(Long teacherId) {
        if (teacherId == null) return empty();
        return buildDistribution(teacherId);
    }

    /**
     * 构建分布：
     * - teacherId=null → 全平台
     * - teacherId!=null → 只算该教师课程
     */
    private CoursewareTypeDistributionVO buildDistribution(Long teacherId) {
        // 1) 4 种课程维度：单条 SQL 按 teacherId/全平台 过滤后 GROUP BY course_type
        // 不使用 qw.select(Course::getCourseType) 避免触发 MyBatis-Plus lambda cache 初始化
        // （全字段查询对 N<10K 的课程表性能影响可忽略）
        LambdaQueryWrapper<Course> courseQw = new LambdaQueryWrapper<>();
        if (teacherId != null) {
            courseQw.eq(Course::getTeacherId, teacherId);
        }
        courseQw.isNull(Course::getDeletedAt);
        List<Course> courses = courseRepository.selectList(courseQw);

        long video = 0, html = 0, ppt = 0, offline = 0;
        for (Course c : courses) {
            String type = c.getCourseType();
            if ("VIDEO".equals(type)) video++;
            else if ("HTML_COURSEWARE".equals(type)) html++;
            else if ("PPT_COURSEWARE".equals(type)) ppt++;
            else if ("OFFLINE".equals(type)) offline++;
            // 忽略其他值（如历史遗留的 INTERACTIVE 已被 V333 迁移消除）
        }

        // 2) 章节维度：含 EXERCISE 章节的去重课程数
        LambdaQueryWrapper<CourseSection> sectionQw = new LambdaQueryWrapper<>();
        sectionQw.eq(CourseSection::getSectionType, "EXERCISE")
                .isNull(CourseSection::getDeletedAt)
                .select(CourseSection::getCourseId);
        if (teacherId != null) {
            // 限定只统计该教师课程下的练习章节
            Set<Long> teacherCourseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());
            if (teacherCourseIds.isEmpty()) {
                return new CoursewareTypeDistributionVO(video, html, ppt, offline, 0L);
            }
            sectionQw.in(CourseSection::getCourseId, teacherCourseIds);
        }
        List<CourseSection> exerciseSections = courseSectionRepository.selectList(sectionQw);
        Set<Long> coursesWithExercises = new HashSet<>();
        for (CourseSection s : exerciseSections) {
            if (s.getCourseId() != null) coursesWithExercises.add(s.getCourseId());
        }

        return new CoursewareTypeDistributionVO(
                video, html, ppt, offline, (long) coursesWithExercises.size());
    }

    private CoursewareTypeDistributionVO empty() {
        return new CoursewareTypeDistributionVO(0L, 0L, 0L, 0L, 0L);
    }
}
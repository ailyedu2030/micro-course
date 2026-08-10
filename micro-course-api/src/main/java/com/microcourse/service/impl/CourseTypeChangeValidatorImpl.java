package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.service.CourseTypeChangeValidator;
import org.springframework.stereotype.Service;

/**
 * F-2026-08-10-07: 课程类型变更校验器实现
 *
 * 当前策略：course_slides 非空 → 禁止类型变更（与 V333 设计原则对齐：类型创建后固定）。
 * 校验粒度：基于 course_slides 计数（含章节级 PPT 锚点 section 关联的课件）。
 */
@Service
public class CourseTypeChangeValidatorImpl implements CourseTypeChangeValidator {

    private final CourseSlideMapper courseSlideMapper;

    public CourseTypeChangeValidatorImpl(CourseSlideMapper courseSlideMapper) {
        this.courseSlideMapper = courseSlideMapper;
    }

    @Override
    public void validate(Long courseId, String oldCourseType, String newCourseType) {
        // 1) 同类型无需校验
        if (oldCourseType == null || oldCourseType.equals(newCourseType)) return;

        // 2) 检查课件残留（PPT/HTML 课件均通过 course_slides 表管理）
        Long slideCount = courseSlideMapper.selectCount(
            new LambdaQueryWrapper<CourseSlide>().eq(CourseSlide::getCourseId, courseId));
        if (slideCount != null && slideCount > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                "课程已有 " + slideCount + " 份课件，请先删除所有课件再切换课程类型（V333 锁定：类型创建后固定）");
        }
    }
}
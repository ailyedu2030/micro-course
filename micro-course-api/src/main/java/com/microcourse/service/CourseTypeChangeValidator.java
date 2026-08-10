package com.microcourse.service;

/**
 * F-2026-08-10-07: 课程类型变更校验器
 *
 * 集中封装 courseType 变更的副作用校验（避免主 CourseAdminServiceImpl 超 800 行）。
 * 当前实现：
 * - 切换课程类型时检查是否残留课件（V333 设计原则：类型创建后固定）
 * - 未来可扩展：切换是否影响已发布课程的评分/统计维度等
 */
public interface CourseTypeChangeValidator {

    /**
     * 校验课程类型变更是否安全。
     *
     * @param courseId        课程 ID
     * @param oldCourseType   原类型
     * @param newCourseType   新类型
     * @throws com.microcourse.exception.BusinessException 当变更不安全时（如残留课件）
     */
    void validate(Long courseId, String oldCourseType, String newCourseType);
}
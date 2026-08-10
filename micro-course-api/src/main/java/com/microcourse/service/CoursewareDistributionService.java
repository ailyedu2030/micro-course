package com.microcourse.service;

import com.microcourse.dto.CoursewareTypeDistributionVO;

/**
 * 5 种课件/课程类型分布服务（F-2026-08-10-06）
 *
 * 提供 admin 看全平台 / teacher 看自己的 5 类型课程分布聚合。
 */
public interface CoursewareDistributionService {

    /** 全平台 5 类型分布（admin 用） */
    CoursewareTypeDistributionVO getGlobalDistribution();

    /** 单教师 5 类型分布（teacher 用，仅算自己课程） */
    CoursewareTypeDistributionVO getTeacherDistribution(Long teacherId);
}
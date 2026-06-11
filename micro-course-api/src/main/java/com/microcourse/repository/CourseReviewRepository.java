package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseReview;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程评价 Mapper
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Mapper
public interface CourseReviewRepository extends BaseMapper<CourseReview> {
}
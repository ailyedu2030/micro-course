package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseReviewLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程审核日志 Mapper
 */
@Mapper
public interface CourseReviewLogRepository extends BaseMapper<CourseReviewLog> {
}

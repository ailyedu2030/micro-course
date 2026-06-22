package com.microcourse.service;

import com.microcourse.dto.CourseReviewLogVO;

import java.util.List;

/**
 * 课程审核日志服务
 */
public interface CourseReviewLogService {

    /**
     * 按课程ID查询审核日志，按创建时间倒序
     */
    List<CourseReviewLogVO> listByCourse(Long courseId);
}

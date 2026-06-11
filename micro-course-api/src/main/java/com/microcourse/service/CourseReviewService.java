package com.microcourse.service;

import com.microcourse.dto.CourseReviewCreateRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;

/**
 * 课程评价服务接口
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
public interface CourseReviewService {

    /**
     * 创建课程评价
     *
     * @param request 评价创建请求
     * @param userId  当前操作用户ID
     * @return 评价视图对象
     */
    CourseReviewVO create(CourseReviewCreateRequest request, Long userId);

    /**
     * 分页查询课程评价列表
     *
     * @param courseId 课程ID
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<CourseReviewVO> listByCourse(Long courseId, int page, int size);
}
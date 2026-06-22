package com.microcourse.service;

import com.microcourse.dto.CourseReviewRequest;
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
     * @param courseId 课程ID（从路径获取）
     * @param request  评价创建请求
     * @param userId   当前操作用户ID
     * @return 评价视图对象
     */
    CourseReviewVO create(Long courseId, CourseReviewRequest request, Long userId);

    /**
     * 分页查询课程评价列表
     *
     * @param courseId 课程ID
     * @param page     页码（从0开始）
     * @param size     每页大小
     * @return 分页结果
     */
    PageResult<CourseReviewVO> listByCourse(Long courseId, int page, int size);

    /**
     * 分页查询当前用户的所有评价
     */
    PageResult<CourseReviewVO> getMyReviews(Long userId, int page, int size);

    /**
     * 管理后台：分页查询所有评价（可按课程筛选）
     */
    PageResult<CourseReviewVO> listAll(int page, int size, Long courseId);

    /**
     * 删除评价
     */
    void deleteReview(Long id);

    /**
     * E4: 查询某条评价的回复列表
     * @param parentId 父评价ID
     * @return 回复列表
     */
    java.util.List<CourseReviewVO> listReplies(Long parentId);

    /**
     * 审核通过评价
     * @param id 评价ID
     */
    void approveReview(Long id);

    /**
     * 审核驳回评价（逻辑驳回，不物理删除）
     * @param id 评价ID
     */
    void rejectReview(Long id);
}
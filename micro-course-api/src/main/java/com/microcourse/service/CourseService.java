package com.microcourse.service;

import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;

public interface CourseService {

    PageResult<CourseVO> page(CoursePageQuery query);

    CourseVO getById(Long id);

    CourseVO create(CourseCreateRequest request);

    CourseVO update(Long id, CourseUpdateRequest request);

    void updateStatus(Long id, Integer status);

    /**
     * 提交课程审核（草稿 → 待审核）
     * @param id 课程ID
     */
    void submitForReview(Long id);

    /**
     * 审核通过（待审核 → 已通过）
     * @param id 课程ID
     */
    void approve(Long id);

    /**
     * 审核拒绝（待审核 → 已驳回）
     * @param id 课程ID
     * @param reason 拒绝原因
     */
    void reject(Long id, String reason);

    /**
     * 发布课程（已通过 → 已发布）
     * @param id 课程ID
     */
    void publish(Long id);

    void delete(Long id);
}
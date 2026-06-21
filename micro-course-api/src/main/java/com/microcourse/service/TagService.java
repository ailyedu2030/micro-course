package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagVO;

import java.util.List;

public interface TagService {

    PageResult<TagVO> page(int page, int size);

    TagVO create(TagCreateRequest request);

    /**
     * Round 5-3 (P1-10): 获取某课程的标签列表（公开读）。
     *
     * @param courseId 课程 ID
     * @return 标签 VO 列表
     */
    List<TagVO> getCourseTags(Long courseId);

    /**
     * Round 5-3 (P1-10): 为课程添加标签（TEACHER 必须课主 / ADMIN）。幂等：已存在则跳过。
     *
     * @param courseId 课程 ID
     * @param tagId    标签 ID
     */
    void addCourseTag(Long courseId, Long tagId);

    /**
     * Round 5-3 (P1-10): 移除课程标签（TEACHER 必须课主 / ADMIN）。
     *
     * @param courseId 课程 ID
     * @param tagId    标签 ID
     */
    void removeCourseTag(Long courseId, Long tagId);
}
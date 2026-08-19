package com.microcourse.service;

import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.ChapterSortRequest;
import com.microcourse.dto.PageResult;
import java.util.List;

public interface CourseChapterService {

    PageResult<ChapterVO> page(int page, int size, Long courseId);

    /**
     * P1-C: 按关键字搜索章节（不要求 courseId）。
     * 前端 GET /api/courses/chapters/search 调用，支持跨课程关键字搜索。
     */
    PageResult<ChapterVO> searchChapters(String keyword, int page, int size);

    ChapterVO getById(Long id);

    ChapterVO create(ChapterCreateRequest request);

    ChapterVO update(Long id, ChapterUpdateRequest request);

    void delete(Long id);

    void sort(List<ChapterSortRequest> requests);

    /** P1 Stage 5: 批量创建章 */
    java.util.List<ChapterVO> batchCreate(Long courseId, java.util.List<ChapterCreateRequest> requests);
}
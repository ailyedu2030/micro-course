package com.microcourse.service;

import com.microcourse.dto.ChapterCreateRequest;
import com.microcourse.dto.ChapterUpdateRequest;
import com.microcourse.dto.ChapterVO;
import com.microcourse.dto.ChapterSortRequest;
import com.microcourse.dto.PageResult;
import java.util.List;

public interface CourseChapterService {

    PageResult<ChapterVO> page(int page, int size, Long courseId);

    ChapterVO getById(Long id);

    ChapterVO create(ChapterCreateRequest request);

    ChapterVO update(Long id, ChapterUpdateRequest request);

    void delete(Long id);

    void sort(List<ChapterSortRequest> requests);
}
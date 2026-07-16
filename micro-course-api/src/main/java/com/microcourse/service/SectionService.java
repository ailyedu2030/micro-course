package com.microcourse.service;

import com.microcourse.dto.*;

public interface SectionService {
    PageResult<SectionDTO> listByChapter(Long chapterId, int page, int size);
    SectionDTO getById(Long id);
    SectionDTO create(Long courseId, Long chapterId, SectionCreateRequest request);
    SectionDTO update(Long id, SectionUpdateRequest request);
    void delete(Long id, boolean force);

    /** P1 Stage 5: 批量创建 section */
    java.util.List<SectionDTO> batchCreate(Long courseId, Long chapterId, java.util.List<SectionCreateRequest> requests);
}

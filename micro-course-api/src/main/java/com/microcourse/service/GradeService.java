package com.microcourse.service;

import com.microcourse.dto.*;

public interface GradeService {

    PageResult<GradeVO> page(Long courseId, Long studentId, int page, int size);

    GradeVO getById(Long id);

    GradeVO create(GradeCreateRequest request, Long teacherId);

    GradeVO update(Long id, GradeUpdateRequest request, Long teacherId);

    void delete(Long id);
}
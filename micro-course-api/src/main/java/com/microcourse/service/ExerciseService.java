package com.microcourse.service;

import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;

public interface ExerciseService {

    ExerciseVO create(ExerciseCreateRequest request);

    ExerciseVO update(Long id, ExerciseUpdateRequest request);

    void delete(Long id);

    PageResult<ExerciseVO> page(Integer courseId, Integer chapterId, Integer page, Integer size);

    ExerciseVO getById(Long id);
}
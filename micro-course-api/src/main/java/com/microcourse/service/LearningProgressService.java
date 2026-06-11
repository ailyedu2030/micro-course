package com.microcourse.service;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;

import java.util.List;

public interface LearningProgressService {

    List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId);

    void updateProgress(Long id, ProgressUpdateRequest request);

    LearningProgressVO create(ProgressCreateRequest request);

    Double getCourseCompletion(Long userId, Long courseId);
}
package com.microcourse.service;

import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.ProgressUpdateRequest;

import java.util.List;
import java.util.Map;

public interface LearningProgressService {

    List<LearningProgressVO> getByUserAndCourse(Long userId, Long courseId);

    void updateProgress(Long id, Long userId, ProgressUpdateRequest request);

    LearningProgressVO create(ProgressCreateRequest request);

    Map<String, Object> getCourseCompletion(Long userId, Long courseId);
}

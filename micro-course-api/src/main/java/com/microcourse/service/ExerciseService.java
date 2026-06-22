package com.microcourse.service;

import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;

import java.util.List;

public interface ExerciseService {

    ExerciseVO create(ExerciseCreateRequest request);

    ExerciseVO update(Long id, ExerciseUpdateRequest request);

    void delete(Long id);

    PageResult<ExerciseVO> page(Integer courseId, Integer chapterId, Integer page, Integer size);

    ExerciseVO getById(Long id);

    void addQuestions(Long exerciseId, List<Long> questionIds);

    void removeQuestion(Long exerciseId, Long questionId);

    /**
     * J3-01: 获取当前学生的考试列表（已选课且 is_exam=true 的练习）
     */
    List<ExerciseVO> getMyExams(Long userId);
}
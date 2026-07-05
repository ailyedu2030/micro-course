package com.microcourse.service;

import com.microcourse.dto.ExamGenerateRequest;
import com.microcourse.dto.ExerciseCreateRequest;
import com.microcourse.dto.ExerciseUpdateRequest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;

import java.util.List;
import java.util.Map;

public interface ExerciseService {

    ExerciseVO create(ExerciseCreateRequest request);

    ExerciseVO update(Long id, ExerciseUpdateRequest request);

    void delete(Long id);

    PageResult<ExerciseVO> page(Long courseId, Long chapterId, Boolean isExam, Integer page, Integer size);

    ExerciseVO getById(Long id);

    void addQuestions(Long exerciseId, List<Long> questionIds);

    void removeQuestion(Long exerciseId, Long questionId);

    /**
     * J3-01: 获取当前学生的考试列表（已选课且 is_exam=true 的练习）
     */
    List<ExerciseVO> getMyExams(Long userId);

    /**
     * 智能组卷：从题库按条件随机抽题，创建考试。
     */
    ExerciseVO generateExam(ExamGenerateRequest req);

    /**
     * 重做练习：校验剩余答题次数并返回下一次答题元信息。
     * @param id 练习ID
     * @param userId 当前用户ID
     * @return { exerciseId, attemptsUsed, maxAttempts, remainingAttempts, nextAttemptNo, canRetry }
     */
    Map<String, Object> retryExercise(Long id, Long userId);
}
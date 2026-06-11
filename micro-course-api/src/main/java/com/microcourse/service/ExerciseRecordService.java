package com.microcourse.service;

import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;

import java.util.List;

public interface ExerciseRecordService {

    /**
     * 提交答案并自动批改
     */
    ExerciseRecordVO submitAnswer(SubmitAnswerRequest request);

    /**
     * 获取某练习的所有答题记录
     */
    List<ExerciseRecordVO> getRecordsByExercise(Long exerciseId);

    /**
     * 获取某用户在某练习的所有记录
     */
    List<ExerciseRecordVO> getMyRecords(Long userId, Long exerciseId);

    /**
     * 获取答题记录详情
     */
    ExerciseRecordVO getRecordById(Long id, Long userId);
}
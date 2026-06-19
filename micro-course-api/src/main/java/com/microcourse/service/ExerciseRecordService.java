package com.microcourse.service;

import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.SubmitAnswerRequest;

import java.util.List;
import java.util.Map;

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

    /**
     * 获取最近N天每日正确率趋势
     * @param userId 用户ID
     * @param days 天数
     * @return [{date, accuracy, totalCount, correctCount}]
     */
    List<Map<String, Object>> getAccuracyTrend(Long userId, int days);

    /**
     * 获取当前用户在某练习的已完成答题次数（用于前端刷新后恢复 attemptNo）
     * @param userId     用户ID
     * @param exerciseId 练习ID
     * @return 已完成答题次数
     */
    int getAttemptCount(Long userId, Long exerciseId);
}
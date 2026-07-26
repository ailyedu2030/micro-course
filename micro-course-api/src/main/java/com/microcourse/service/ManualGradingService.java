package com.microcourse.service;

/**
 * 教师手动批改主观题服务。
 * <p>
 * 从 GradeService 中拆分，职责单一：处理教师对练习中主观题的手动阅卷，
 * 包括写回 answers JSON、重算得分、同步 grades 表、发送通知和审计追踪。
 * </p>
 */
public interface ManualGradingService {

    /**
     * 教师手动批改主观题：将单题 score/comment 写回 answers JSON，并同步更新记录与 grades 表。
     *
     * @param recordId  练习记录 ID
     * @param questionId 题目 ID（answers JSON 中的 questionId）
     * @param score      得分
     * @param comment    评语
     * @param teacherId  批改教师 ID
     */
    void manualGrade(Long recordId, Long questionId, Double score, String comment, Long teacherId);
}

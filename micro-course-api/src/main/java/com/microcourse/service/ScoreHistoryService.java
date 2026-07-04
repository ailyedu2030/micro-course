package com.microcourse.service;

import com.microcourse.dto.ScoreHistoryVO;

import java.util.List;

public interface ScoreHistoryService {

    /** 记录一条成绩变更历史 */
    void recordChange(Long enrollmentId, String fieldName, String oldValue,
                     String newValue, String changeType, String reason, Long operatorId);

    /** 查询某 enrollment 的所有变更 */
    List<ScoreHistoryVO> listByEnrollment(Long enrollmentId);
}

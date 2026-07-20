package com.microcourse.service;

import com.microcourse.dto.BatchOperationResult;
import java.util.List;

public interface CourseAuditService {

    void submitForReview(Long id);

    void approve(Long id);

    void reject(Long id, String reason);

    void publish(Long id);

    void unpublish(Long id);

    BatchOperationResult batchApprove(List<Long> ids);

    /** P1C-S17: 带版本号校验的批量审核通过 */
    BatchOperationResult batchApprove(List<Long> ids, java.util.Map<Long, Long> idVersionMap);

    BatchOperationResult batchReject(List<Long> ids, String reason);
}

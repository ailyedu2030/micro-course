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

    BatchOperationResult batchReject(List<Long> ids, String reason);
}

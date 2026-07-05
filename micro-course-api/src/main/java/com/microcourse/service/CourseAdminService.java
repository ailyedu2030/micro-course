package com.microcourse.service;

import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.CourseVO;
import org.springframework.web.multipart.MultipartFile;

public interface CourseAdminService {

    CourseVO create(com.microcourse.dto.CourseCreateRequest request);

    CourseVO update(Long id, com.microcourse.dto.CourseUpdateRequest request);

    void delete(Long id);

    CourseVO copy(Long id);

    CourseVO updateCover(Long id, MultipartFile file);

    void updateStatus(Long id, Integer status);

    void submitForReview(Long id);

    void approve(Long id);

    void reject(Long id, String reason);

    void publish(Long id);

    void unpublish(Long id);

    /** P2-11: 批量审核通过 */
    BatchOperationResult batchApprove(java.util.List<Long> ids);

    /** P2-11: 批量审核驳回 */
    BatchOperationResult batchReject(java.util.List<Long> ids, String reason);
}

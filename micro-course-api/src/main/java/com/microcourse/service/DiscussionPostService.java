package com.microcourse.service;

import com.microcourse.dto.DiscussionPostVO;
import com.microcourse.dto.DiscussionPageQuery;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PostCreateRequest;
import com.microcourse.dto.PostUpdateRequest;

public interface DiscussionPostService {

    PageResult<DiscussionPostVO> page(Long chapterId, int page, int size);

    PageResult<DiscussionPostVO> pageAdmin(DiscussionPageQuery query);

    DiscussionPostVO getById(Long id);

    DiscussionPostVO create(PostCreateRequest req, Long userId);

    DiscussionPostVO update(Long id, PostUpdateRequest request, Long userId);

    void delete(Long id, Long userId);

    void pin(Long id);

    void updatePin(Long id, boolean pinned);

    void updateEssence(Long id, boolean essence);

    /**
     * 更新讨论状态（审核通过/驳回）
     */
    void updateStatus(Long id, String status);

    /**
     * P1C-060: 驳回讨论并填写驳回原因
     */
    void rejectWithReason(Long id, String reason);

    /**
     * P1#12: 帖子点赞
     */
    void like(Long postId, Long userId);
}

package com.microcourse.service;

import com.microcourse.dto.VideoBookmarkCreateRequest;
import com.microcourse.dto.VideoBookmarkVO;

import java.util.List;

/**
 * 视频书签服务
 */
public interface VideoBookmarkService {

    /**
     * 查询指定视频的当前用户书签列表
     */
    List<VideoBookmarkVO> listByVideoId(Long videoId);

    /**
     * 创建书签
     */
    VideoBookmarkVO create(Long videoId, VideoBookmarkCreateRequest request);

    /**
     * 删除书签（仅本人可删）
     */
    void delete(Long videoId, Long bookmarkId);
}

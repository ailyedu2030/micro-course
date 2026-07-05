package com.microcourse.service;

import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Video;
import org.springframework.web.multipart.MultipartFile;

public interface VideoService {

    PageResult<VideoVO> page(Long courseId, int page, int size);

    VideoVO getById(Long id);

    @Deprecated
    Video findEntityById(Long id);

    VideoVO create(VideoCreateRequest request);

    Video createEntity(Video video);

    VideoVO update(Long id, VideoUpdateRequest request);

    void delete(Long id);

    String uploadCover(Long videoId, MultipartFile file);

    /**
     * 更新视频状态(0=UPLOADING,1=TRANSCODING,2=COMPLETED,3=FAILED)。
     * 用于异步上传/转码失败时将卡住的状态推进,避免脏数据。
     */
    void updateStatus(Long videoId, int status);

    /**
     * P0-2: 校验当前用户是否为课程 owner 或 ADMIN
     */
    void assertCourseOwnership(Long courseId);

    /**
     * P1-6: 校验章节归属课程
     */
    void assertChapterBelongsToCourse(Long chapterId, Long courseId);

    /**
     * P2: 按 MD5 查询是否已有重复视频（秒传）
     */
    @Deprecated
    Video findByMd5(String md5);
}

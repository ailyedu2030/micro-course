package com.microcourse.service;

import com.microcourse.dto.VideoCreateRequest;
import org.springframework.web.multipart.MultipartFile;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Video;

public interface VideoService {

    PageResult<VideoVO> page(Long courseId, int page, int size);

    VideoVO getById(Long id);

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
}
package com.microcourse.service;

import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoUpdateRequest;
import com.microcourse.dto.VideoVO;
import com.microcourse.dto.PageResult;

public interface VideoService {

    PageResult<VideoVO> page(Long courseId, int page, int size);

    VideoVO getById(Long id);

    VideoVO create(VideoCreateRequest request);

    VideoVO update(Long id, VideoUpdateRequest request);

    void delete(Long id);
}
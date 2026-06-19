package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.VideoBookmarkCreateRequest;
import com.microcourse.dto.VideoBookmarkVO;
import com.microcourse.entity.Video;
import com.microcourse.entity.VideoBookmark;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.VideoBookmarkRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoBookmarkService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VideoBookmarkServiceImpl implements VideoBookmarkService {

    private final VideoBookmarkRepository bookmarkRepository;
    private final VideoRepository videoRepository;

    public VideoBookmarkServiceImpl(VideoBookmarkRepository bookmarkRepository,
                                    VideoRepository videoRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.videoRepository = videoRepository;
    }

    @Override
    public List<VideoBookmarkVO> listByVideoId(Long videoId) {
        Long userId = SecurityUtil.getCurrentUserId();
        LambdaQueryWrapper<VideoBookmark> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VideoBookmark::getVideoId, videoId)
                .eq(VideoBookmark::getUserId, userId)
                .orderByAsc(VideoBookmark::getPosition);
        return bookmarkRepository.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoBookmarkVO create(Long videoId, VideoBookmarkCreateRequest request) {
        // 校验视频存在
        Video video = videoRepository.selectById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }

        Long userId = SecurityUtil.getCurrentUserId();

        VideoBookmark bookmark = new VideoBookmark();
        bookmark.setVideoId(videoId);
        bookmark.setUserId(userId);
        bookmark.setPosition(request.getPosition());
        bookmark.setLabel(request.getLabel());
        bookmark.setNote(request.getNote());
        bookmark.setCreatedAt(LocalDateTime.now());

        bookmarkRepository.insert(bookmark);
        return toVO(bookmark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long videoId, Long bookmarkId) {
        VideoBookmark bookmark = bookmarkRepository.selectById(bookmarkId);
        if (bookmark == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "书签不存在");
        }
        // 校验归属：仅本人可删
        Long userId = SecurityUtil.getCurrentUserId();
        if (!userId.equals(bookmark.getUserId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // 校验 videoId 一致
        if (!videoId.equals(bookmark.getVideoId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "书签不属于该视频");
        }
        bookmarkRepository.deleteById(bookmarkId);
    }

    private VideoBookmarkVO toVO(VideoBookmark bookmark) {
        VideoBookmarkVO vo = new VideoBookmarkVO();
        vo.setId(bookmark.getId());
        vo.setUserId(bookmark.getUserId());
        vo.setVideoId(bookmark.getVideoId());
        vo.setPosition(bookmark.getPosition());
        vo.setLabel(bookmark.getLabel());
        vo.setNote(bookmark.getNote());
        vo.setCreatedAt(bookmark.getCreatedAt());
        return vo;
    }
}

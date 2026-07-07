package com.microcourse.util;

import com.microcourse.dto.VideoVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.Video;

import java.util.Map;

/**
 * Video → VideoVO 转换工具 (从 VideoServiceImpl 提取以减少 800 行)
 */
public final class VideoConverter {

    private VideoConverter() {}

    /**
     * 填充 VideoVO 基本字段 (不依赖外部 Map, 用于 N+1 优化)
     */
    public static VideoVO populateBasicFields(Video video) {
        VideoVO vo = new VideoVO();
        vo.setId(video.getId());
        vo.setChapterId(video.getChapterId());
        vo.setCourseId(video.getCourseId());
        vo.setTitle(video.getTitle());
        vo.setFileName(video.getFileName());
        vo.setFileSize(video.getFileSize());
        vo.setFileMd5(video.getFileMd5());
        vo.setMimeType(video.getMimeType());
        vo.setDuration(video.getDuration());
        vo.setUrl(video.getUrl());
        vo.setHlsUrl(video.getHlsUrl());
        vo.setThumbnailUrl(video.getThumbnailUrl());
        vo.setCoverUrl(video.getCoverUrl());
        vo.setStatus(video.getStatus());
        vo.setProgress(video.getProgress());
        vo.setErrorMessage(video.getErrorMessage());
        vo.setOriginalPath(video.getOriginalPath());
        vo.setSortOrder(video.getSortOrder());
        vo.setCreatedAt(video.getCreatedAt());
        vo.setUpdatedAt(video.getUpdatedAt());
        vo.setVersion(video.getVersion());
        return vo;
    }

    /**
     * 填充课程/章节名称 (使用预加载的 Map, 避免 N+1)
     */
    public static VideoVO convertToVO(Video video, Map<Long, Course> courseMap,
                                      Map<Long, CourseChapter> chapterMap) {
        VideoVO vo = populateBasicFields(video);

        if (video.getCourseId() != null) {
            Course course = courseMap.get(video.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }

        if (video.getChapterId() != null && chapterMap != null) {
            CourseChapter chapter = chapterMap.get(video.getChapterId());
            if (chapter != null) {
                vo.setChapterName(chapter.getTitle());
            }
        }

        return vo;
    }
}
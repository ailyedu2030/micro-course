package com.microcourse.service;

import com.microcourse.dto.VideoVO;
import com.microcourse.entity.Video;
import com.microcourse.enums.VideoStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.VideoBookmarkRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.impl.VideoServiceImpl;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.VideoSignUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceImplRetryTranscodeTest {

    @Test
    void retryTranscodeShouldResubmitAsyncJob() {
        MybatisPlusTestHelper.initTableInfo();
        VideoRepository videoRepository = mock(VideoRepository.class);
        VideoTranscodeService videoTranscodeService = mock(VideoTranscodeService.class);
        VideoServiceImpl spyService = spy(buildService(videoRepository, videoTranscodeService));

        Video video = new Video();
        video.setId(9L);
        video.setCourseId(88L);
        video.setStatus(VideoStatus.FAILED.getCode());

        VideoVO expected = new VideoVO();
        expected.setId(9L);
        expected.setStatus(VideoStatus.TRANSCODING.getCode());

        when(videoRepository.selectById(9L)).thenReturn(video);
        when(videoRepository.update(eq(null), any())).thenReturn(1);
        doNothing().when(spyService).assertCourseOwnership(88L);
        doReturn(expected).when(spyService).getById(9L);

        VideoVO result = spyService.retryTranscode(9L);

        assertSame(expected, result);
        verify(spyService).getById(9L);
        verify(videoTranscodeService).transcode(9L);
    }

    @Test
    void retryTranscodeShouldFailClosedWhenStatusAlreadyChanged() {
        MybatisPlusTestHelper.initTableInfo();
        VideoRepository videoRepository = mock(VideoRepository.class);
        VideoTranscodeService videoTranscodeService = mock(VideoTranscodeService.class);
        VideoServiceImpl spyService = spy(buildService(videoRepository, videoTranscodeService));

        Video video = new Video();
        video.setId(10L);
        video.setCourseId(99L);
        video.setStatus(VideoStatus.FAILED.getCode());

        when(videoRepository.selectById(10L)).thenReturn(video);
        when(videoRepository.update(eq(null), any())).thenReturn(0);
        doNothing().when(spyService).assertCourseOwnership(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> spyService.retryTranscode(10L));

        assertEquals(ErrorCode.MS_CONCURRENT_MODIFICATION.getCode(), ex.getCode());
        verify(videoTranscodeService, never()).transcode(10L);
    }

    private static VideoServiceImpl buildService(VideoRepository videoRepository,
                                                 VideoTranscodeService videoTranscodeService) {
        return new VideoServiceImpl(
                videoRepository,
                mock(CourseChapterRepository.class),
                mock(CourseRepository.class),
                mock(VideoBookmarkRepository.class),
                videoTranscodeService,
                mock(VideoAccessService.class),
                mock(VideoSignUtil.class),
                mock(AdminSettingService.class),
                mock(RedisUtil.class),
                mock(LearningProgressRepository.class)
        );
    }
}

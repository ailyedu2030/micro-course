package com.microcourse.service.impl;

import com.microcourse.entity.Course;
import com.microcourse.entity.User;
import com.microcourse.entity.Video;
import com.microcourse.enums.VideoStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.VideoTranscodeService;
import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * VideoUploadService 单元测试 (Phase 11)
 *
 * <p>验证视频上传 / 封面上传 / 批量上传的委托逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VideoUploadService 视频上传单元测试")
class VideoUploadServiceTest {

    @Mock private VideoRepository videoRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserRepository userRepository;
    @Mock private VideoTranscodeService videoTranscodeService;
    @Mock private RedisUtil redisUtil;
    @Mock private VideoValidator videoValidator;

    private VideoUploadService executor;

    @BeforeEach
    void setUp() {
        executor = new VideoUploadService(
                videoRepository, courseRepository, userRepository,
                videoTranscodeService, redisUtil, videoValidator,
                "/tmp/videos",  // uploadDir (来自 @Value)
                "/tmp/covers"   // coverDir (来自 @Value)
        );
    }

    @Test
    @DisplayName("batchUpload: 空文件数组 → 抛 BAD_REQUEST_PARAM")
    void batchUpload_emptyFiles() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.batchUpload(null, 1L, null));
        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("batchUpload: 零长度数组 → 抛 BAD_REQUEST_PARAM")
    void batchUpload_zeroLengthArray() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.batchUpload(new MultipartFile[0], 1L, null));
        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("batchUpload: 委托 assertCourseOwnership → validator 被调用")
    void batchUpload_delegatesToValidator() {
        // 不实际调用 uploadVideo (会触发文件 IO), 用 lenient
        lenient().doNothing().when(videoValidator).assertCourseOwnership(anyLong());
        lenient().doNothing().when(videoValidator).assertChapterBelongsToCourse(anyLong(), anyLong());

        // Mock 异常路径: 当 validator 抛错时, batchUpload 透传
        doThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND))
                .when(videoValidator).assertCourseOwnership(999L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.batchUpload(new MultipartFile[]{null}, 999L, null));
        assertEquals(ErrorCode.COURSE_NOT_FOUND.getCode(), ex.getCode());
        verify(videoValidator).assertCourseOwnership(999L);
    }
}
package com.microcourse.service;

import com.microcourse.dto.VideoCreateRequest;
import com.microcourse.dto.VideoStatusVO;
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
     * 上传视频文件：保存文件、计算MD5、去重秒传、创建Video实体、异步转码。
     *
     * @param file      上传的视频文件
     * @param courseId  所属课程ID
     * @param chapterId 所属章节ID（可选）
     * @return 视频VO
     */
    VideoVO uploadVideo(MultipartFile file, Long courseId, Long chapterId);

    /**
     * 获取视频播放HLS URL：包含视频存在校验、选课校验（STUDENT）、签名校验。
     *
     * @param id   视频ID
     * @param sign 播放签名
     * @return HLS播放URL
     * @throws BusinessException 校验不通过时抛出对应异常
     */
    String getHlsPlayUrl(Long id, String sign);

    /**
     * 获取视频所属课程 ID（同时验证视频存在性）。
     *
     * @param videoId 视频ID
     * @return 课程ID
     * @throws BusinessException VIDEO_NOT_FOUND 当视频不存在时
     */
    Long getCourseIdByVideoId(Long videoId);

    /**
     * 获取视频转码状态（轻量级轮询接口）
     *
     * @param id 视频ID
     * @return VideoStatusVO (videoId, status, statusLabel, progress, errorMessage)
     */
    VideoStatusVO getStatus(Long id);

    /**
     * 更新视频状态(0=UPLOADING,1=TRANSCODING,2=COMPLETED,3=FAILED)。
     * 用于异步上传/转码失败时将卡住的状态推进,避免脏数据。
     */
    void updateStatus(Long videoId, int status);

    /**
     * 【权限矩阵 v4.0 §3.5】重试失败的转码任务
     * @param id 视频 ID
     * @return 更新后的 VideoVO (status=1 表示重新进入转码中)
     */
    VideoVO retryTranscode(Long id);

    /**
     * 【权限矩阵 v4.0 §3.5】视频播放分析
     * @param id 视频 ID
     * @return VideoAnalyticsVO (播放次数, 平均观看时长, 完成率)
     */
    com.microcourse.dto.VideoAnalyticsVO getAnalytics(Long id);

    /**
     * 【权限矩阵 v4.0 §3.5】批量上传视频文件
     * @param files 多个视频文件
     * @param courseId 所属课程
     * @param chapterId 所属章节
     * @return 上传结果列表 (每项包含 videoId, filename, status)
     */
    java.util.List<VideoVO> batchUpload(org.springframework.web.multipart.MultipartFile[] files,
                                        Long courseId, Long chapterId);

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

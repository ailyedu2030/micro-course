package com.microcourse.service;

/**
 * 视频转码服务
 *
 * 依据：Phase 8 开发规范
 * 状态机：UPLOADING(0) → TRANSCODING(1) → COMPLETED(2)/FAILED(3)
 */
public interface VideoTranscodeService {

    /**
     * 异步执行 FFmpeg HLS 转码
     * 读取 Video.originalPath，输出 /data/videos/{courseId}/{videoId}/index.m3u8
     * 转码进度每 10% 更新一次
     *
     * @param videoId 视频记录 ID
     */
    void transcode(Long videoId);
}

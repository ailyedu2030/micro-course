package com.microcourse.service;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

/**
 * HLS 视频流服务（供 /api/video-stream 与旧路径 /api/videos/stream 别名共用）。
 */
public interface VideoStreamService {

    /**
     * 流式返回 HLS 分片（m3u8 / ts），含访问权限 + 签名 + 路径穿越三重校验。
     */
    ResponseEntity<Resource> stream(Long courseId, Long videoId, String filename, String sign);
}

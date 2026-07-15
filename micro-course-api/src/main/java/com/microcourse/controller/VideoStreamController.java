package com.microcourse.controller;

import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.repository.CourseRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.VideoAccessService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoSignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HLS 视频流代理
 *
 * P0-1 修复：302 重定向到文件系统路径 → 通过 HTTP 端点流式返回 HLS 内容
 * 同时代理 .m3u8 和 .ts 分片文件
 */
@RestController
@RequestMapping("/api/video-stream")
@Tag(name = "VideoStreamController", description = "VideoStreamController 自动生成 OpenAPI 文档")
public class VideoStreamController {

    private static final Logger log = LoggerFactory.getLogger(VideoStreamController.class);

    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    private final VideoAccessService videoAccessService;
    private final VideoSignUtil videoSignUtil;
    private final CourseRepository courseRepository;

    public VideoStreamController(VideoAccessService videoAccessService,
                                  VideoSignUtil videoSignUtil,
                                  CourseRepository courseRepository) {
        this.videoAccessService = videoAccessService;
        this.videoSignUtil = videoSignUtil;
        this.courseRepository = courseRepository;
    }

    /**
     * 流式返回 HLS 文件（.m3u8 / .ts）
     *
     * 路径格式：/api/video-stream/{courseId}/{videoId}/{filename}
     * filename 可以是 index.m3u8 或 segment0.ts 等
     */
    @GetMapping("/{courseId}/{videoId}/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> stream(
            @PathVariable Long courseId,
            @PathVariable Long videoId,
            @PathVariable String filename,
            @RequestParam(value = "sign", required = false) String sign) {

        // P1I-014: 为所有角色添加签名校验，防止非 STUDENT 角色绕过视频流防护
        if (SecurityUtil.hasRole("STUDENT")) {
            // STUDENT: 先选课校验（未选课返回 8005 NOT_ENROLLED，而非 12003 VIDEO_SIGN_INVALID）
            VideoAccessService.AccessResult access =
                    videoAccessService.checkVideoAccess(SecurityUtil.getCurrentUserId(), courseId);
            if (!access.allowed) {
                throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再观看视频");
            }
        } else if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            // TEACHER: 校验是否为课程所有者，防止访问他人课程视频
            Course course = courseRepository.selectById(courseId);
            if (course == null) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            if (!course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "只能访问自己课程的视频");
            }
        }
        // 所有角色都需要验证视频签名，防止直接访问绕过
        if (sign == null || !videoSignUtil.verifySign(videoId, sign)) {
            throw new BusinessException(ErrorCode.VIDEO_SIGN_INVALID, "视频签名无效或已过期");
        }

        // 安全校验：文件名不能包含路径穿越字符（先URL decode防止双编码绕过）
        String decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        if (decodedFilename.contains("..") || decodedFilename.contains("/") || decodedFilename.contains("\\")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "非法文件名");
        }
        // 使用解码后的文件名进行后续路径构造
        filename = decodedFilename;

        // 限制文件扩展名
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".m3u8") && !lower.endsWith(".ts")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 .m3u8 和 .ts 文件");
        }

        Path filePath = Paths.get(storageBaseDir, String.valueOf(courseId),
                String.valueOf(videoId), filename);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND, "HLS 文件不存在");
        }

        // 确保路径在 storageBaseDir 内，防止穿越
        Path basePath = Paths.get(storageBaseDir).toAbsolutePath().normalize();
        Path resolvedPath = filePath.toAbsolutePath().normalize();
        if (!resolvedPath.startsWith(basePath)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "路径非法");
        }

        String contentType = lower.endsWith(".m3u8")
                ? "application/vnd.apple.mpegurl"
                : "video/mp2t";

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .body(resource);
    }
}

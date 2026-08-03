package com.microcourse.service.impl;

import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.VideoAccessService;
import com.microcourse.service.VideoStreamService;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.VideoSignUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * HLS 流服务实现：从 VideoStreamController 抽出的共享逻辑，
 * 供标准路径 /api/video-stream 与兼容旧路径 /api/videos/stream 共同调用。
 */
@Service
public class VideoStreamServiceImpl implements VideoStreamService {

    @Value("${video.storage-base-dir:/data/videos}")
    private String storageBaseDir;

    private final VideoAccessService videoAccessService;
    private final VideoSignUtil videoSignUtil;
    private final CourseRepository courseRepository;

    public VideoStreamServiceImpl(VideoAccessService videoAccessService,
                                  VideoSignUtil videoSignUtil,
                                  CourseRepository courseRepository) {
        this.videoAccessService = videoAccessService;
        this.videoSignUtil = videoSignUtil;
        this.courseRepository = courseRepository;
    }

    @Override
    public ResponseEntity<Resource> stream(Long courseId, Long videoId, String filename, String sign) {
        // P1I-014: 为所有角色添加签名校验，防止非 STUDENT 角色绕过视频流防护
        Long userId = SecurityUtil.getCurrentUserId();
        if (!SecurityUtil.isAdminOrAcademic()) {
            // TEACHER/STUDENT: 校验访问权限
            Course course = courseRepository.selectById(courseId);
            if (course == null) {
                throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
            }
            if (!userId.equals(course.getTeacherId())) {
                // 非课程所有者（STUDENT 或非所有者 TEACHER）→ 必须已选课
                VideoAccessService.AccessResult access =
                        videoAccessService.checkVideoAccess(userId, courseId);
                if (!access.allowed) {
                    throw new BusinessException(ErrorCode.NOT_ENROLLED, "请先选课后再观看视频");
                }
            }
            // 课程所有者（TEACHER）→ 允许访问
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

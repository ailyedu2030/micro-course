package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 课件读侧统一入口 (CQRS Query).
 *
 * 单一职责: 给前端一个 GET 拿全课件树, 替代 N+1 拼装.
 * 路径: /api/courses/{courseId}/courseware/...
 *
 * 7-19 P1-C 兼容:
 * <ul>
 *   <li>GET /audio/{token} 不依赖 pageNumber, 仅用 audio_token UK 校验</li>
 *   <li>流式 GET p99 < 100ms 首字节 (nginx 直发静态文件)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/courses/{courseId}/courseware")
public class CoursewareQueryController {

    private final CoursewareQueryService queryService;

    @Autowired
    public CoursewareQueryController(CoursewareQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 取课件完整树 (PPT pages + scripts + audios + flow / HTML unit + segments).
     * 前端调这一个 endpoint 即可拿到所有播放所需数据.
     */
    @GetMapping("/{sectionId}")
    public R<CoursewareTreeDTO> getCoursewareTree(@PathVariable Long courseId,
                                                   @PathVariable Long sectionId) {
        return R.ok(queryService.getCoursewareTree(sectionId));
    }

    /**
     * 音频流式 GET (7-19 P1-C 修复兼容).
     * URL: GET /api/courses/{courseId}/audio/{token}
     * 不依赖 pageNumber, 仅用 audio_token.
     */
    @GetMapping("/audio/{token}")
    public void streamAudio(@PathVariable Long courseId,
                             @PathVariable String token,
                             HttpServletResponse response) throws IOException {
        AudioStreamInfo info = queryService.resolveAudioToken(token);
        if (!"READY".equals(info.getStatus())) {
            response.setStatus(HttpServletResponse.SC_ACCEPTED);  // 202
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"status\":\"" + info.getStatus() + "\",\"message\":\"Audio not ready yet\"}");
            return;
        }
        if (info.getStoragePath() == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path filePath = Paths.get(info.getStoragePath());
        if (!Files.exists(filePath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_LENGTH,
                String.valueOf(Files.size(filePath)));
        try (InputStream in = Files.newInputStream(filePath)) {
            in.transferTo(response.getOutputStream());
        }
    }
}
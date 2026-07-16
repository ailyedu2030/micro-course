package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.service.CourseChapterService;
import com.microcourse.service.CourseQueryService;
import com.microcourse.service.SectionService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 路径别名控制器(P1 Stage 4-5)
 *
 * Trae SKILL.md 期望的 RESTful 路径与平台实际路径不一致时的别名映射。
 * 所有方法直接委托给对应 Service 的已有实现。
 *
 * R2 审查修复:
 * - P0: uploadHtml 绕过 HTML 灰度白名单 → 增加 whitelist 校验
 * - P0: uploadPpt 绕过 PPT 魔数校验 → 增加 validateSlideFileMagic
 * - P1: batch sections 路径缺 /chapters/ 段 → 修正
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
public class AliasController {

    private final CourseChapterService chapterService;
    private final SlideService slideService;
    private final CourseQueryService courseQueryService;
    private final SectionService sectionService;

    @Value("${plugin.interactive.html-content.whitelist-teachers:}")
    private java.util.List<Long> htmlWhitelist;

    public AliasController(CourseChapterService chapterService, SlideService slideService,
                           CourseQueryService courseQueryService, SectionService sectionService) {
        this.chapterService = chapterService;
        this.slideService = slideService;
        this.courseQueryService = courseQueryService;
        this.sectionService = sectionService;
    }

    @PostMapping("/chapters")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<ChapterVO> createChapter(@PathVariable Long courseId,
                                      @Valid @RequestBody ChapterCreateRequest request) {
        request.setCourseId(courseId);
        return R.ok(chapterService.create(request));
    }

    @PostMapping("/sections/{sectionId}/html")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> uploadHtml(@PathVariable Long courseId,
                                              @PathVariable Long sectionId,
                                              @RequestParam("file") MultipartFile file) {
        // R2 审查 P0: HTML 灰度白名单 — 与 SlideController 保持一致
        if (!SecurityUtil.isAdmin() && !htmlWhitelist.isEmpty()
                && !htmlWhitelist.contains(SecurityUtil.getCurrentUserId())) {
            throw new com.microcourse.exception.BusinessException(
                com.microcourse.exception.ErrorCode.NO_PERMISSION,
                "HTML 课件上传功能灰度中，仅白名单教师可使用");
        }
        SlideUploadResponse resp = slideService.uploadHtmlFile(courseId, file, null, sectionId);
        return R.ok(resp);
    }

    @PostMapping("/sections/{sectionId}/ppt")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> uploadPpt(@PathVariable Long courseId,
                                             @PathVariable Long sectionId,
                                             @RequestParam("file") MultipartFile file) {
        try {
            // R2 审查 P0: PPT 魔数校验
            validateSlideFileMagic(file);
            SlideUploadResponse resp = slideService.upload(
                courseId, file.getOriginalFilename(), file.getBytes(), null, sectionId);
            return R.ok(resp);
        } catch (IOException e) {
            throw new com.microcourse.exception.BusinessException(
                com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM, "PPT 文件读取失败: " + e.getMessage());
        }
    }

    // ===== P1 Stage 5: 幂等性 + 批量化 =====

    @PostMapping("/chapters/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<ChapterVO>> batchCreateChapters(@PathVariable Long courseId,
                                                     @Valid @RequestBody List<ChapterCreateRequest> requests) {
        return R.ok(chapterService.batchCreate(courseId, requests));
    }

    // R2 审查 P1: 路径缺 /chapters/ 段 (原 /{chapterId}/sections/batch → 修正)
    @PostMapping("/chapters/{chapterId}/sections/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<SectionDTO>> batchCreateSections(@PathVariable Long courseId,
                                                      @PathVariable Long chapterId,
                                                      @Valid @RequestBody List<SectionCreateRequest> requests) {
        return R.ok(sectionService.batchCreate(courseId, chapterId, requests));
    }

    // ===== private helpers =====

    private void validateSlideFileMagic(MultipartFile file) throws IOException {
        byte[] magic = new byte[4];
        try (java.io.InputStream is = file.getInputStream()) {
            int read = is.read(magic);
            if (read < 4 || !isZipHeader(magic)) {
                throw new com.microcourse.exception.BusinessException(
                    com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM, "文件不是 PPTX 格式(ZIP 魔数校验失败)");
            }
        }
    }

    private boolean isZipHeader(byte[] b) {
        return b.length >= 4 && b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04;
    }
}

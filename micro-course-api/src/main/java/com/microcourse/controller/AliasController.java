package com.microcourse.controller;

import com.microcourse.dto.*;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.service.CourseChapterService;
import com.microcourse.service.CourseQueryService;
import com.microcourse.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 路径别名控制器(P1 Stage 4)
 *
 * Trae SKILL.md 期望的 RESTful 路径与平台实际路径不一致时的别名映射。
 * 所有方法直接委托给对应 Service 的已有实现。
 */
@RestController
@RequestMapping("/api/courses/{courseId}")
// 不加 @ConditionalOnProperty：chapter 别名不依赖 interactive 插件
// html/ppt 端点虽依赖 SlideService，但交互式插件默认启用
public class AliasController {

    private final CourseChapterService chapterService;
    private final SlideService slideService;
    private final CourseQueryService courseQueryService;
    private final SectionService sectionService;

    public AliasController(CourseChapterService chapterService, SlideService slideService,
                           CourseQueryService courseQueryService, SectionService sectionService) {
        this.chapterService = chapterService;
        this.slideService = slideService;
        this.courseQueryService = courseQueryService;
        this.sectionService = sectionService;
    }

    /**
     * Alias for POST /api/chapters
     * Trae 期望: POST /api/courses/{courseId}/chapters
     */
    @PostMapping("/chapters")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<ChapterVO> createChapter(@PathVariable Long courseId,
                                      @Valid @RequestBody ChapterCreateRequest request) {
        request.setCourseId(courseId);
        ChapterVO vo = chapterService.create(request);
        return R.ok(vo);
    }

    /**
     * Alias for POST /api/courses/{courseId}/slides/upload (HTML branch)
     * Trae 期望: POST /api/courses/{courseId}/sections/{sectionId}/html
     */
    @PostMapping("/sections/{sectionId}/html")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> uploadHtml(@PathVariable Long courseId,
                                              @PathVariable Long sectionId,
                                              @RequestParam("file") MultipartFile file) {
        SlideUploadResponse resp = slideService.uploadHtmlFile(courseId, file, null, sectionId);
        return R.ok(resp);
    }

    /**
     * Alias for POST /api/courses/{courseId}/slides/upload (PPT branch)
     * Trae 期望: POST /api/courses/{courseId}/sections/{sectionId}/ppt
     */
    @PostMapping("/sections/{sectionId}/ppt")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlideUploadResponse> uploadPpt(@PathVariable Long courseId,
                                             @PathVariable Long sectionId,
                                             @RequestParam("file") MultipartFile file) {
        try {
            SlideUploadResponse resp = slideService.upload(
                courseId, file.getOriginalFilename(), file.getBytes(), null, sectionId);
            return R.ok(resp);
        } catch (java.io.IOException e) {
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

    @PostMapping("/{chapterId}/sections/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<SectionDTO>> batchCreateSections(@PathVariable Long courseId,
                                                     @PathVariable Long chapterId,
                                                     @Valid @RequestBody List<SectionCreateRequest> requests) {
        return R.ok(sectionService.batchCreate(courseId, chapterId, requests));
    }
}

package com.microcourse.controller;

import com.microcourse.audit.AuditedLog;
import com.microcourse.dto.BatchOperationResult;
import com.microcourse.dto.R;
import com.microcourse.service.CoursewareDeleteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师自主删除课件 API (sytafe 需求 2026-07-20).
 *
 * <p>提供 4 个维度:
 * <ul>
 *   <li>单条删除: chapter / section / ppt_page / html_unit</li>
 *   <li>批量删除: chapters / ppt_pages</li>
 * </ul>
 * </p>
 *
 * <p>权限: @PreAuthorize 仅允许 TEACHER / ADMIN 进入,
 * Service 层进一步校验"课主" (IDOR 防御).</p>
 *
 * <p>软删除: deleted_at = now(), 保留审计痕迹.</p>
 */
@RestController
@RequestMapping("/api/courses/{courseId}/courseware")
@Tag(name = "课件自主删除", description = "教师自主删除 chapter/section/slide 课件")
@Validated
public class CoursewareDeleteController {

    private final CoursewareDeleteService coursewareDeleteService;

    public CoursewareDeleteController(CoursewareDeleteService coursewareDeleteService) {
        this.coursewareDeleteService = coursewareDeleteService;
    }

    // ====================================================================
    // 单条删除
    // ====================================================================

    @DeleteMapping("/chapters/{chapterId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师删除 chapter")
    @Operation(summary = "删除 chapter (级联删其下 section + PPT/HTML 课件)")
    public R<CoursewareDeleteService.DeleteStats> deleteChapter(
            @PathVariable @Parameter(description = "课程 ID") Long courseId,
            @PathVariable @Parameter(description = "chapter ID") Long chapterId) {
        return R.ok(coursewareDeleteService.deleteChapter(courseId, chapterId));
    }

    @DeleteMapping("/sections/{sectionId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师删除 section")
    @Operation(summary = "删除 section (级联删其下 PPT/HTML 课件)")
    public R<CoursewareDeleteService.DeleteStats> deleteSection(
            @PathVariable Long courseId,
            @PathVariable Long sectionId) {
        return R.ok(coursewareDeleteService.deleteSection(courseId, sectionId));
    }

    @DeleteMapping("/ppt-pages/{pptPageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师删除 PPT page")
    @Operation(summary = "删除 PPT page (含 script)")
    public R<CoursewareDeleteService.DeleteStats> deletePptPage(
            @PathVariable Long courseId,
            @PathVariable Long pptPageId) {
        return R.ok(coursewareDeleteService.deletePptPage(courseId, pptPageId));
    }

    @DeleteMapping("/html-units/{htmlUnitId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师删除 HTML unit")
    @Operation(summary = "删除 HTML unit (含 segment scripts)")
    public R<CoursewareDeleteService.DeleteStats> deleteHtmlUnit(
            @PathVariable Long courseId,
            @PathVariable Long htmlUnitId) {
        return R.ok(coursewareDeleteService.deleteHtmlUnit(courseId, htmlUnitId));
    }

    // ====================================================================
    // 批量删除
    // ====================================================================

    @DeleteMapping("/chapters/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师批量删除 chapter")
    @Operation(summary = "批量删除 chapter (限 100)")
    public R<BatchOperationResult> deleteChaptersBatch(
            @PathVariable Long courseId,
            @RequestBody @NotEmpty(message = "chapterIds 不能为空") List<Long> chapterIds) {
        return R.ok(coursewareDeleteService.deleteChaptersBatch(courseId, chapterIds));
    }

    @DeleteMapping("/ppt-pages/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @AuditedLog("教师批量删除 PPT page")
    @Operation(summary = "批量删除 PPT page (限 500)")
    public R<BatchOperationResult> deletePptPagesBatch(
            @PathVariable Long courseId,
            @RequestBody @NotEmpty(message = "pptPageIds 不能为空") List<Long> pptPageIds) {
        return R.ok(coursewareDeleteService.deletePptPagesBatch(courseId, pptPageIds));
    }
}
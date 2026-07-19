package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * HTML 课件 REST API (spec 4.3).
 *
 * 路径: /api/courses/{courseId}/html/...
 * 角色: TEACHER / ADMIN (写) + 任意登录用户 (读)
 *
 * 7-19 P0 防御: createUnit 走 HtmlCoursewareService.createUnit (in-place UPSERT),
 * 后端强制 HtmlSanitizer.sanitizeForCourseware, 即使前端漏掉 sanitize 也安全.
 */
@RestController
@RequestMapping("/api/courses/{courseId}/html")
public class HtmlCoursewareController {

    private final HtmlCoursewareService htmlService;

    @Autowired
    public HtmlCoursewareController(HtmlCoursewareService htmlService) {
        this.htmlService = htmlService;
    }

    // ====== Units ======

    @GetMapping("/sections/{sectionId}/unit")
    public R<SlideHtmlUnitDTO> getUnitBySection(@PathVariable Long courseId,
                                                 @PathVariable Long sectionId) {
        return R.ok(htmlService.getUnitBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/unit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createUnit(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody SlideHtmlUnitDTO dto) {
        dto.setCourseId(courseId);
        if (dto.getSectionId() == null) dto.setSectionId(sectionId);
        return R.ok(htmlService.createUnit(dto));
    }

    @GetMapping("/units/{unitId}")
    public R<SlideHtmlUnitDTO> getUnit(@PathVariable Long courseId,
                                        @PathVariable Long unitId) {
        return R.ok(htmlService.getUnit(unitId));
    }

    @PutMapping("/units/{unitId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updateUnit(@PathVariable Long courseId,
                               @PathVariable Long unitId,
                               @RequestBody SlideHtmlUnitDTO dto) {
        htmlService.updateUnit(unitId, dto);
        return R.ok();
    }

    @DeleteMapping("/units/{unitId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteUnit(@PathVariable Long courseId,
                               @PathVariable Long unitId) {
        htmlService.deleteUnit(unitId);
        return R.ok();
    }

    // ====== Segment Scripts ======

    @GetMapping("/units/{unitId}/segments")
    public R<List<HtmlSegmentScriptDTO>> listActiveSegments(@PathVariable Long courseId,
                                                             @PathVariable Long unitId) {
        return R.ok(htmlService.listActiveSegments(unitId));
    }

    @GetMapping("/units/{unitId}/segments/{idx}")
    public R<HtmlSegmentScriptDTO> getActiveSegment(@PathVariable Long courseId,
                                                    @PathVariable Long unitId,
                                                    @PathVariable Integer idx) {
        return R.ok(htmlService.getActiveSegmentScript(unitId, idx));
    }

    @PutMapping("/units/{unitId}/segments/{idx}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> saveSegmentScript(@PathVariable Long courseId,
                                      @PathVariable Long unitId,
                                      @PathVariable Integer idx,
                                      @RequestBody SaveSegmentScriptRequest body) {
        return R.ok(htmlService.saveSegmentScript(unitId, idx, body.scriptText(),
                body.voice(), body.ttsModel(), body.segmentMarker(), body.createdBy()));
    }

    // ====== Segment Audios ======

    @GetMapping("/segments/{scriptId}/audios")
    public R<List<HtmlSegmentAudioDTO>> listSegmentAudios(@PathVariable Long courseId,
                                                           @PathVariable Long scriptId) {
        return R.ok(htmlService.listSegmentAudios(scriptId));
    }

    @PostMapping("/segments/{scriptId}/audios")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> generateSegmentAudio(@PathVariable Long courseId,
                                         @PathVariable Long scriptId,
                                         @RequestBody GenerateSegmentAudioRequest body) {
        return R.ok(htmlService.generateSegmentAudio(scriptId, body.voice(),
                body.model(), body.ttsParams()));
    }

    // ====== Request bodies ======

    public record SaveSegmentScriptRequest(String scriptText, String voice, String ttsModel,
                                            String segmentMarker, Long createdBy) {}
    public record GenerateSegmentAudioRequest(String voice, String model, String ttsParams) {}
}
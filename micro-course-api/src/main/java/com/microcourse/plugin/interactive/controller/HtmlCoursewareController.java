package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SegmentDetectionResult;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
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

    public HtmlCoursewareController(HtmlCoursewareService htmlService) {
        this.htmlService = htmlService;
    }

    // ====== Units ======

    @GetMapping("/sections/{sectionId}/unit")
    public R<SlideHtmlUnitDTO> getUnitBySection(@PathVariable Long courseId,
                                                 @PathVariable Long sectionId) {
        // P0-2 IDOR: 读端点同样校验 — 防越权读取他人课程课件内容
        htmlService.verifySectionUnitOwner(courseId, sectionId);
        return R.ok(htmlService.getUnitBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/unit")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createUnit(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody SlideHtmlUnitDTO dto) {
        // P0-2 IDOR: section 必须属于该课程 + 当前用户是 owner
        htmlService.verifySectionUnitOwner(courseId, sectionId);
        dto.setCourseId(courseId);
        if (dto.getSectionId() == null) dto.setSectionId(sectionId);
        return R.ok(htmlService.createUnit(dto));
    }

    @GetMapping("/units/{unitId}")
    public R<SlideHtmlUnitDTO> getUnit(@PathVariable Long courseId,
                                        @PathVariable Long unitId) {
        // P0-2 IDOR: 关键校验 — unitId 必须属于 courseId, 防任意登录用户读取任意课程课件
        htmlService.verifyUnitOwner(courseId, unitId);
        return R.ok(htmlService.getUnit(unitId));
    }

    @PutMapping("/units/{unitId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updateUnit(@PathVariable Long courseId,
                               @PathVariable Long unitId,
                               @RequestBody SlideHtmlUnitDTO dto) {
        // P0-2 IDOR: unit 必须属于该课程 + 当前用户是 owner
        htmlService.verifyUnitOwner(courseId, unitId);
        htmlService.updateUnit(unitId, dto);
        return R.ok();
    }

    @DeleteMapping("/units/{unitId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteUnit(@PathVariable Long courseId,
                               @PathVariable Long unitId) {
        // P0-2 IDOR: unit 必须属于该课程 + 当前用户是 owner
        htmlService.verifyUnitOwner(courseId, unitId);
        htmlService.deleteUnit(unitId);
        return R.ok();
    }

    // ====== P2-1 自动分段检测 ======

    /**
     * 对 unit 的 sanitize 后内容运行启发式分段检测（标题/段落边界），
     * 落库 slide_html_units.detected_segments 并返回段列表（含 marker/selector）。
     * owner 校验在 service 层（G1 IDOR 协同，unit 必须属于当前用户可写的课程）。
     */
    @PostMapping("/units/{unitId}/detect")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SegmentDetectionResult> detectSegments(@PathVariable Long courseId,
                                                     @PathVariable Long unitId) {
        return R.ok(htmlService.runDetection(unitId));
    }

    // ====== Segment Scripts ======

    @GetMapping("/units/{unitId}/segments")
    public R<List<HtmlSegmentScriptDTO>> listActiveSegments(@PathVariable Long courseId,
                                                             @PathVariable Long unitId) {
        // D-1 IDOR 修复 (读端点同校验): unitId 必须属于 courseId + 当前用户是 owner
        htmlService.verifyUnitOwner(courseId, unitId);
        return R.ok(htmlService.listActiveSegments(unitId));
    }

    @GetMapping("/units/{unitId}/segments/{idx}")
    public R<HtmlSegmentScriptDTO> getActiveSegment(@PathVariable Long courseId,
                                                    @PathVariable Long unitId,
                                                    @PathVariable Integer idx) {
        // D-1 IDOR 修复 (读端点同校验): unitId 必须属于 courseId + 当前用户是 owner
        htmlService.verifyUnitOwner(courseId, unitId);
        return R.ok(htmlService.getActiveSegmentScript(unitId, idx));
    }

    @PutMapping("/units/{unitId}/segments/{idx}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> saveSegmentScript(@PathVariable Long courseId,
                                      @PathVariable Long unitId,
                                      @PathVariable Integer idx,
                                      @RequestBody SaveSegmentScriptRequest body) {
        // P0-2 IDOR: unit 必须属于该课程 + 当前用户是 owner
        htmlService.verifyUnitOwner(courseId, unitId);
        return R.ok(htmlService.saveSegmentScript(unitId, idx, body.scriptText(),
                body.voice(), body.ttsModel(), body.segmentMarker(), body.createdBy()));
    }

    // ====== Segment Audios ======

    @GetMapping("/segments/{scriptId}/audios")
    public R<List<HtmlSegmentAudioDTO>> listSegmentAudios(@PathVariable Long courseId,
                                                           @PathVariable Long scriptId) {
        // D-1 IDOR 修复 (读端点同校验): segment script 所属 unit 必须属于 courseId
        // + 当前用户是 owner (audioToken 是流媒体唯一凭证, 防越权读取音频)
        htmlService.verifySegmentScriptOwner(courseId, scriptId);
        return R.ok(htmlService.listSegmentAudios(scriptId));
    }

    @PostMapping("/segments/{scriptId}/audios")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> generateSegmentAudio(@PathVariable Long courseId,
                                         @PathVariable Long scriptId,
                                         @RequestBody GenerateSegmentAudioRequest body) {
        // P0-2 IDOR: segment script 所属 unit 必须属于该课程 + 当前用户是 owner
        // (TTS 计费端点 — 防止消耗他人 TTS 额度)
        htmlService.verifySegmentScriptOwner(courseId, scriptId);
        return R.ok(htmlService.generateSegmentAudio(scriptId, body.voice(),
                body.model(), body.ttsParams()));
    }

    // ====== Request bodies ======

    public record SaveSegmentScriptRequest(String scriptText, String voice, String ttsModel,
                                            String segmentMarker, Long createdBy) {}
    public record GenerateSegmentAudioRequest(String voice, String model, String ttsParams) {}
}
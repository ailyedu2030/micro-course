package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PPT 课件 REST API (spec 4.3).
 *
 * 路径: /api/courses/{courseId}/ppt/...
 * 角色: TEACHER / ADMIN (写) + 任意登录用户 (读)
 */
@RestController
@RequestMapping("/api/courses/{courseId}/ppt")
public class PptCoursewareController {

    private final PptCoursewareService pptService;

    @Autowired
    public PptCoursewareController(PptCoursewareService pptService) {
        this.pptService = pptService;
    }

    // ====== Pages ======

    @GetMapping("/sections/{sectionId}/pages")
    public R<List<SlidePptPageDTO>> listPages(@PathVariable Long courseId,
                                               @PathVariable Long sectionId) {
        return R.ok(pptService.listPagesBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/pages")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createPage(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody SlidePptPageDTO dto) {
        dto.setCourseId(courseId);
        if (dto.getSectionId() == null) dto.setSectionId(sectionId);
        return R.ok(pptService.createPage(dto));
    }

    @GetMapping("/pages/{pageId}")
    public R<SlidePptPageDTO> getPage(@PathVariable Long courseId,
                                       @PathVariable Long pageId) {
        return R.ok(pptService.getPage(pageId));
    }

    @PutMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updatePage(@PathVariable Long courseId,
                               @PathVariable Long pageId,
                               @RequestBody SlidePptPageDTO dto) {
        pptService.updatePage(pageId, dto);
        return R.ok();
    }

    @DeleteMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deletePage(@PathVariable Long courseId,
                               @PathVariable Long pageId) {
        pptService.deletePage(pageId);
        return R.ok();
    }

    // ====== Scripts ======

    @GetMapping("/pages/{pageId}/scripts/active")
    public R<PptScriptDTO> getActiveScript(@PathVariable Long courseId,
                                            @PathVariable Long pageId) {
        return R.ok(pptService.getActiveScript(pageId));
    }

    @GetMapping("/pages/{pageId}/scripts")
    public R<List<PptScriptDTO>> listScriptHistory(@PathVariable Long courseId,
                                                    @PathVariable Long pageId) {
        return R.ok(pptService.listScriptHistory(pageId));
    }

    @PutMapping("/pages/{pageId}/scripts")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> saveScript(@PathVariable Long courseId,
                               @PathVariable Long pageId,
                               @RequestBody SaveScriptRequest body) {
        return R.ok(pptService.saveScript(pageId, body.scriptText(),
                body.voice(), body.ttsModel(), body.createdBy()));
    }

    // ====== Audios ======

    @GetMapping("/scripts/{scriptId}/audios")
    public R<List<PptAudioDTO>> listAudios(@PathVariable Long courseId,
                                            @PathVariable Long scriptId) {
        return R.ok(pptService.listAudios(scriptId));
    }

    @PostMapping("/scripts/{scriptId}/audios")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> generateAudio(@PathVariable Long courseId,
                                  @PathVariable Long scriptId,
                                  @RequestBody GenerateAudioRequest body) {
        return R.ok(pptService.generateAudio(scriptId, body.voice(),
                body.model(), body.ttsParams()));
    }

    @GetMapping("/audios/{audioId}")
    public R<PptAudioDTO> getAudio(@PathVariable Long courseId,
                                    @PathVariable Long audioId) {
        return R.ok(pptService.getAudio(courseId, audioId));
    }

    // ====== Flows ======

    @GetMapping("/sections/{sectionId}/flows")
    public R<List<PptFlowDTO>> listFlows(@PathVariable Long courseId,
                                          @PathVariable Long sectionId) {
        return R.ok(pptService.listFlowsBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/flows")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createFlow(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody PptFlowDTO dto) {
        dto.setSectionId(sectionId);
        return R.ok(pptService.createFlow(dto));
    }

    // ====== Request bodies ======

    public record SaveScriptRequest(String scriptText, String voice, String ttsModel, Long createdBy) {}
    public record GenerateAudioRequest(String voice, String model, String ttsParams) {}
}
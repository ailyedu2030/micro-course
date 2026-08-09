package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
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

    public PptCoursewareController(PptCoursewareService pptService) {
        this.pptService = pptService;
    }

    // ====== Pages ======

    @GetMapping("/sections/{sectionId}/pages")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<SlidePptPageDTO>> listPages(@PathVariable Long courseId,
                                               @PathVariable Long sectionId) {
        // D-2 IDOR 修复 (读端点同校验): section 必须属于该课程 + 当前用户是 owner
        pptService.verifySectionOwner(courseId, sectionId);
        return R.ok(pptService.listPagesBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/pages")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createPage(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody SlidePptPageDTO dto) {
        // P0-1 IDOR: section 必须属于该课程 + 当前用户是 owner
        pptService.verifySectionOwner(courseId, sectionId);
        dto.setCourseId(courseId);
        if (dto.getSectionId() == null) dto.setSectionId(sectionId);
        return R.ok(pptService.createPage(dto));
    }

    @GetMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<SlidePptPageDTO> getPage(@PathVariable Long courseId,
                                       @PathVariable Long pageId) {
        // D-2 IDOR 修复 (读端点同校验): page 必须属于该课程 + 当前用户是 owner
        pptService.verifyPageOwner(courseId, pageId);
        return R.ok(pptService.getPage(pageId));
    }

    @PutMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updatePage(@PathVariable Long courseId,
                               @PathVariable Long pageId,
                               @RequestBody SlidePptPageDTO dto) {
        // P0-1 IDOR: page 必须属于该课程 + 当前用户是 owner
        pptService.verifyPageOwner(courseId, pageId);
        pptService.updatePage(pageId, dto);
        return R.ok();
    }

    @DeleteMapping("/pages/{pageId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deletePage(@PathVariable Long courseId,
                               @PathVariable Long pageId) {
        // P0-1 IDOR: page 必须属于该课程 + 当前用户是 owner
        pptService.verifyPageOwner(courseId, pageId);
        pptService.deletePage(pageId);
        return R.ok();
    }

    // ====== Scripts ======

    @GetMapping("/pages/{pageId}/scripts/active")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PptScriptDTO> getActiveScript(@PathVariable Long courseId,
                                            @PathVariable Long pageId) {
        // D-2 IDOR 修复 (读端点同校验): page 必须属于该课程 + 当前用户是 owner
        pptService.verifyPageOwner(courseId, pageId);
        return R.ok(pptService.getActiveScript(pageId));
    }

    @GetMapping("/pages/{pageId}/scripts")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<PptScriptDTO>> listScriptHistory(@PathVariable Long courseId,
                                                    @PathVariable Long pageId) {
        // D-2 横向扫描 IDOR 修复 (读端点同校验): page 必须属于该课程 + 当前用户是 owner
        // (讲述稿历史与 active 脚本同属敏感内容, 同模式漏网一并修复)
        pptService.verifyPageOwner(courseId, pageId);
        return R.ok(pptService.listScriptHistory(pageId));
    }

    @PutMapping("/pages/{pageId}/scripts")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> saveScript(@PathVariable Long courseId,
                               @PathVariable Long pageId,
                               @RequestBody SaveScriptRequest body) {
        // P0-1 IDOR: page 必须属于该课程 + 当前用户是 owner
        pptService.verifyPageOwner(courseId, pageId);
        return R.ok(pptService.saveScript(pageId, body.scriptText(),
                body.voice(), body.ttsModel(),
                body.createdBy() != null ? body.createdBy()
                        : com.microcourse.util.SecurityUtil.getCurrentUserId()));
    }

    // ====== Audios ======

    @GetMapping("/scripts/{scriptId}/audios")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<PptAudioDTO>> listAudios(@PathVariable Long courseId,
                                            @PathVariable String scriptId) {
        // 容错：前端无脚本时可能传 "null"/非法值，返回空列表而非 500
        Long parsed = parseLongOrNull(scriptId);
        if (parsed == null) {
            return R.ok(new java.util.ArrayList<>());
        }
        // D-2 IDOR 修复 (读端点同校验): script 所属 page 必须属于该课程 + 当前用户是 owner
        // (audioToken 是流媒体唯一凭证, 防越权读取音频)
        pptService.verifyScriptOwner(courseId, parsed);
        return R.ok(pptService.listAudios(parsed));
    }

    private Long parseLongOrNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @PostMapping("/scripts/{scriptId}/audios")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> generateAudio(@PathVariable Long courseId,
                                  @PathVariable Long scriptId,
                                  @RequestBody GenerateAudioRequest body) {
        // P0-1 IDOR: script 所属 page 必须属于该课程 + 当前用户是 owner
        // (TTS 计费端点 — 防止消耗他人 TTS 额度)
        pptService.verifyScriptOwner(courseId, scriptId);
        return R.ok(pptService.generateAudio(scriptId, body.voice(),
                body.model(), body.ttsParams()));
    }

    @GetMapping("/audios/{audioId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<PptAudioDTO> getAudio(@PathVariable Long courseId,
                                    @PathVariable Long audioId) {
        return R.ok(pptService.getAudio(courseId, audioId));
    }

    // ====== Flows ======

    @GetMapping("/sections/{sectionId}/flows")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','ACADEMIC')")
    public R<List<PptFlowDTO>> listFlows(@PathVariable Long courseId,
                                          @PathVariable Long sectionId) {
        // D-2 横向扫描 IDOR 修复 (读端点同校验): section 必须属于该课程 + 当前用户是 owner
        // (跳转规则属课程内部编排逻辑, 同模式漏网一并修复)
        pptService.verifySectionOwner(courseId, sectionId);
        return R.ok(pptService.listFlowsBySection(sectionId));
    }

    @PostMapping("/sections/{sectionId}/flows")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Long> createFlow(@PathVariable Long courseId,
                               @PathVariable Long sectionId,
                               @RequestBody PptFlowDTO dto) {
        // P0-1 IDOR: section 必须属于该课程 + 当前用户是 owner
        pptService.verifySectionOwner(courseId, sectionId);
        dto.setSectionId(sectionId);
        return R.ok(pptService.createFlow(dto));
    }

    @PutMapping("/flows/{flowId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> updateFlow(@PathVariable Long courseId,
                              @PathVariable Long flowId,
                              @RequestBody PptFlowDTO dto) {
        // P0-1 IDOR: flow 所属 section 必须属于该课程 + 当前用户是 owner
        pptService.verifyFlowOwner(courseId, flowId);
        pptService.updateFlow(flowId, dto);
        return R.ok();
    }

    @DeleteMapping("/flows/{flowId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> deleteFlow(@PathVariable Long courseId,
                              @PathVariable Long flowId) {
        // P0-1 IDOR: flow 所属 section 必须属于该课程 + 当前用户是 owner
        pptService.verifyFlowOwner(courseId, flowId);
        pptService.deleteFlow(flowId);
        return R.ok();
    }

    // ====== Request bodies ======

    public record SaveScriptRequest(String scriptText, String voice, String ttsModel, Long createdBy) {}
    public record GenerateAudioRequest(String voice, String model, String ttsParams) {}
}

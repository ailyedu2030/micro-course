package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.TtsGenerateRequest;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;
import com.microcourse.plugin.interactive.service.TtsService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class TtsController {

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping("/pages/{pageNumber}/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> generate(@PathVariable Long courseId,
                                    @PathVariable Integer pageNumber,
                                    @RequestParam(required = false) Long sectionId) {
        return R.ok(ttsService.generate(courseId, pageNumber));
    }

    @PostMapping("/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> generateAll(@PathVariable Long courseId) {
        ttsService.generateAll(courseId);
        return R.ok();
    }

    @GetMapping("/pages/{pageNumber}/audio")
    public ResponseEntity<byte[]> getAudio(@PathVariable Long courseId,
                                            @PathVariable Integer pageNumber,
                                            @RequestParam(required = false) Long sectionId,
                                            @RequestParam(required = false) String token) {
        if (token != null && !token.isBlank()) {
            if (!ttsService.validateAudioToken(courseId, pageNumber, sectionId, token)) {
                throw new com.microcourse.exception.BusinessException(
                        com.microcourse.exception.ErrorCode.NO_PERMISSION, "无效的音频访问令牌");
            }
        } else {
            ttsService.verifyAccess(courseId);
        }
        byte[] audioBytes = ttsService.getAudio(courseId, pageNumber, sectionId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(1, TimeUnit.HOURS).getHeaderValue())
                .body(audioBytes);
    }

    @PostMapping("/sections/{sectionId}/tts/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<TtsStatusResponse> generateSection(@PathVariable Long courseId,
                                                 @PathVariable Long sectionId,
                                                 @Valid @RequestBody TtsGenerateRequest request) {
        return R.ok(ttsService.generateSection(courseId, sectionId,
                request.getVoice(), request.getModel(),
                request.getSpeed(), request.getSplitByPage()).join());
    }

    @GetMapping("/sections/{sectionId}/tts/status")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<TtsStatusResponse> getSectionTtsStatus(@PathVariable Long courseId,
                                                     @PathVariable Long sectionId,
                                                     @RequestParam String taskId) {
        return R.ok(ttsService.getSectionTtsStatus(courseId, sectionId, taskId));
    }
}

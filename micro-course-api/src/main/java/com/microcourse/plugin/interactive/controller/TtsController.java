package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.service.TtsService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
public class TtsController {

    private final TtsService ttsService;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @PostMapping("/pages/{pageNumber}/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> generate(@PathVariable Long courseId,
                                    @PathVariable Integer pageNumber) {
        return R.ok(ttsService.generate(courseId, pageNumber));
    }

    @PostMapping("/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> generateAll(@PathVariable Long courseId) {
        ttsService.generateAll(courseId);
        return R.ok();
    }

    @GetMapping("/pages/{pageNumber}/audio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getAudio(@PathVariable Long courseId,
                                            @PathVariable Integer pageNumber) {
        try {
            Path audioPath = Paths.get(storagePath, String.valueOf(courseId),
                    "audio", "page_" + pageNumber + ".mp3");
            byte[] audioBytes = Files.readAllBytes(audioPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.CACHE_CONTROL,
                            CacheControl.maxAge(1, TimeUnit.HOURS).getHeaderValue())
                    .body(audioBytes);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.AudioUploadResponse;
import com.microcourse.plugin.interactive.service.AudioUploadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}/audio")
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class AudioUploadController {

    private final AudioUploadService audioUploadService;

    public AudioUploadController(AudioUploadService audioUploadService) {
        this.audioUploadService = audioUploadService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<AudioUploadResponse> uploadSingle(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestParam("file") MultipartFile file) {
        return R.ok(audioUploadService.uploadSingle(courseId, sectionId, file));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<AudioUploadResponse> uploadBatch(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestParam(value = "file_1", required = false) MultipartFile file1,
            @RequestParam(value = "file_2", required = false) MultipartFile file2,
            @RequestParam(value = "file_3", required = false) MultipartFile file3,
            @RequestParam(value = "file_4", required = false) MultipartFile file4,
            @RequestParam(value = "file_5", required = false) MultipartFile file5,
            @RequestParam(value = "file_6", required = false) MultipartFile file6,
            @RequestParam(value = "file_7", required = false) MultipartFile file7,
            @RequestParam(value = "file_8", required = false) MultipartFile file8,
            @RequestParam(value = "file_9", required = false) MultipartFile file9,
            @RequestParam(value = "file_10", required = false) MultipartFile file10,
            @RequestParam(value = "file_11", required = false) MultipartFile file11,
            @RequestParam(value = "file_12", required = false) MultipartFile file12,
            @RequestParam(value = "file_13", required = false) MultipartFile file13,
            @RequestParam(value = "file_14", required = false) MultipartFile file14,
            @RequestParam(value = "file_15", required = false) MultipartFile file15) {
        List<MultipartFile> files = Stream.of(
                file1, file2, file3, file4, file5,
                file6, file7, file8, file9, file10,
                file11, file12, file13, file14, file15)
                .filter(Objects::nonNull)
                .toList();
        return R.ok(audioUploadService.uploadBatch(courseId, sectionId, files));
    }
}

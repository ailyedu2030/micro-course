package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.AudioUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AudioUploadService {

    AudioUploadResponse uploadSingle(Long courseId, Long sectionId, MultipartFile file);

    AudioUploadResponse uploadBatch(Long courseId, Long sectionId, List<MultipartFile> files);

    void verifyOwnership(Long courseId, Long sectionId);
}

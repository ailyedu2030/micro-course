package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TtsService {

    SlidePageVO generate(Long courseId, Integer pageNumber);

    void generateAll(Long courseId);

    CompletableFuture<TtsStatusResponse> generateSection(Long courseId, Long sectionId,
                                                        String voice, String model, Double speed,
                                                        boolean splitByPage);

    TtsStatusResponse getSectionTtsStatus(Long courseId, Long sectionId, String taskId);

    List<TtsStatusResponse> generateSectionsBatch(Long courseId, List<Long> sectionIds,
                                                  String voice, String model, Double speed,
                                                  boolean splitByPage);

    byte[] getAudio(Long courseId, Integer pageNumber, Long sectionId);

    void verifyAccess(Long courseId);
}

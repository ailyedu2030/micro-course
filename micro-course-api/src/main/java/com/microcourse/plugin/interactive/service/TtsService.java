package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;

public interface TtsService {

    SlidePageVO generate(Long courseId, Integer pageNumber);

    void generateAll(Long courseId);

    byte[] getAudio(Long courseId, Integer pageNumber);
}

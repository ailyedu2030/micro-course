package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;

public interface NarrationService {

    SlidePageVO generate(Long courseId, Integer pageNumber);

    SlidePageVO updateScript(Long courseId, Integer pageNumber, String narrationScript);

    void generateAll(Long courseId);
}

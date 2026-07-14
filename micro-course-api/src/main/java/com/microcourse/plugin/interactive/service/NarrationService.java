package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;

public interface NarrationService {

    SlidePageVO generate(Long courseId, Integer pageNumber, Long sectionId);

    SlidePageVO updateScript(Long courseId, Integer pageNumber, Long sectionId, String narrationScript);

    void generateAll(Long courseId);
}

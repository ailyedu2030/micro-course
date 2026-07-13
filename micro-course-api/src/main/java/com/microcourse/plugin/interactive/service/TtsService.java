package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;

public interface TtsService {

    SlidePageVO generate(Long courseId, Integer pageNumber);

    void generateAll(Long courseId);

    byte[] getAudio(Long courseId, Integer pageNumber, Long sectionId);

    /** 校验当前用户是否有权访问课程的音频（ADMIN/ACADEMIC/TEACHER owner/已选课学生） */
    void verifyAccess(Long courseId);
}

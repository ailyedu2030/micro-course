package com.microcourse.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface HermesWebhookCoursewareService {

    SlideUploadResponse uploadSlide(Long courseId, Long lessonId, MultipartFile file);

    List<SlidePageVO> listSlidePages(Long courseId, Long lessonId);

    SlidePageVO updateSlidePageNarration(Long courseId, Long lessonId, Integer pageNumber, Map<String, Object> body);

    void deleteSlidePage(Long courseId, Long lessonId, Integer pageNumber);

    void deleteSectionCascade(Long courseId, Long sectionId);

    void deleteChapterCascade(Long courseId, Long chapterId);

    void deleteCourseCascade(Long courseId);

    List<SlideVO> listSlides(Long courseId);

    Map<String, Object> batchPushScripts(Long courseId, Long sectionId, Long chapterId, String scriptContent);
}

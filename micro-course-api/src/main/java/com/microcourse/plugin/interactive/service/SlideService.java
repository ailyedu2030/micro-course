package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface SlideService {

    SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes, Long chapterId, Long sectionId);

    SlideUploadResponse uploadHtmlFile(Long courseId, MultipartFile file, Long chapterId, Long sectionId);

    void tryConvertPptxToHtml(Long slideId, byte[] pptxBytes);

    SlideVO getByCourseId(Long courseId);
    List<SlideVO> listByCourseId(Long courseId);

    List<SlidePageVO> getPages(Long courseId, Long sectionId);

    List<SegmentAudioVO> getSegmentAudios(Long courseId, Long sectionId);

    SlidePageVO getPage(Long courseId, Integer pageNumber);

    byte[] getPageImage(Long courseId, Integer pageNumber);

    byte[] getPageThumbnail(Long courseId, Integer pageNumber);

    void deleteSlide(Long courseId, Long sectionId);

    void deletePage(Long courseId, Integer pageNumber, Long sectionId);

    SlidePageVO updatePage(Long courseId, Integer pageNumber, java.util.Map<String, Object> body);

    void reorderPages(Long courseId, java.util.List<java.util.Map<String, Integer>> order);

    byte[] getOriginalFile(Long courseId);

    void cleanupSlideFiles(Long courseId, Long slideId);
}

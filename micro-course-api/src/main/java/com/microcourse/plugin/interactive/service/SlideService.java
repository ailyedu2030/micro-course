package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface SlideService {

    SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes, Long chapterId);

    SlideUploadResponse uploadHtmlFile(Long courseId, MultipartFile file, Long chapterId);

    void tryConvertPptxToHtml(Long slideId, byte[] pptxBytes);

    SlideVO getByCourseId(Long courseId);

    List<SlidePageVO> getPages(Long courseId, Long chapterId);

    SlidePageVO getPage(Long courseId, Integer pageNumber);

    byte[] getPageImage(Long courseId, Integer pageNumber);

    byte[] getPageThumbnail(Long courseId, Integer pageNumber);

    void deleteSlide(Long courseId, Long chapterId);

    void deletePage(Long courseId, Integer pageNumber);

    SlidePageVO updatePage(Long courseId, Integer pageNumber, java.util.Map<String, Object> body);

    void reorderPages(Long courseId, java.util.List<java.util.Map<String, Integer>> order);

    byte[] getOriginalFile(Long courseId);
}

package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;

import java.util.List;

public interface SlideService {

    SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes);

    SlideVO getByCourseId(Long courseId);

    List<SlidePageVO> getPages(Long courseId);

    SlidePageVO getPage(Long courseId, Integer pageNumber);

    byte[] getPageImage(Long courseId, Integer pageNumber);

    byte[] getPageThumbnail(Long courseId, Integer pageNumber);
}

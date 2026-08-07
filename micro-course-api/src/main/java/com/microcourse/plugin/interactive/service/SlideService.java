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

    List<SlidePageVO> getPages(Long courseId, Long sectionId, Long chapterId);

    List<SegmentAudioVO> getSegmentAudios(Long courseId, Long sectionId);

    SlidePageVO getPage(Long courseId, Integer pageNumber);

    byte[] getPageImage(Long courseId, Integer pageNumber);

    byte[] getPageThumbnail(Long courseId, Integer pageNumber);

    void deleteSlide(Long courseId, Long sectionId);

    /**
     * 按课件 ID 删除（DELETE /courses/{courseId}/slides/{slideId}）。
     * P1-C 修复：此前复用按 sectionId 删除的方法，把 slideId 当 sectionId 查询
     * 永远查不到 → 课件删除功能 100% 失效。
     */
    void deleteSlideById(Long courseId, Long slideId);

    /**
     * 整节/整章课件删除（v1 + v2 全量清理，F-2026-08-07-13）。
     */
    void deleteCourseware(Long courseId, Long sectionId, Long chapterId);

    void deletePage(Long courseId, Integer pageNumber, Long sectionId);

    SlidePageVO updatePage(Long courseId, Integer pageNumber, java.util.Map<String, Object> body);

    void reorderPages(Long courseId, java.util.List<java.util.Map<String, Integer>> order);

    byte[] getOriginalFile(Long courseId);

    void cleanupSlideFiles(Long courseId, Long slideId);

    /**
     * 校验文件是否为有效的 PPTX 格式（ZIP 魔数校验）。
     *
     * @param file 上传的文件
     * @throws com.microcourse.exception.BusinessException 如果魔数校验失败
     */
    void validateFileMagic(org.springframework.web.multipart.MultipartFile file);
}

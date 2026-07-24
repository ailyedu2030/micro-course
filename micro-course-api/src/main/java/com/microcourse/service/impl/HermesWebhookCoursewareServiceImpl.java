package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.HermesWebhookCoursewareService;
import com.microcourse.util.XssSanitizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HermesWebhookCoursewareServiceImpl implements HermesWebhookCoursewareService {

    private final CourseSectionRepository sectionRepository;
    private final CourseChapterRepository chapterRepository;
    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final SlideService slideService;

    public HermesWebhookCoursewareServiceImpl(CourseSectionRepository sectionRepository,
                                              CourseChapterRepository chapterRepository,
                                              CourseSlideMapper courseSlideMapper,
                                              SlidePageMapper slidePageMapper,
                                              SlideService slideService) {
        this.sectionRepository = sectionRepository;
        this.chapterRepository = chapterRepository;
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.slideService = slideService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlideUploadResponse uploadSlide(Long courseId, Long lessonId, MultipartFile file) {
        CourseSection section = requireSectionInCourse(courseId, lessonId);
        String filename = requireFilename(file);
        boolean isHtml = isHtmlFile(filename);
        validateUploadFile(file, filename, isHtml);
        try {
            if (isHtml) {
                return slideService.uploadHtmlFile(courseId, file, section.getChapterId(), lessonId);
            }
            return slideService.upload(courseId, filename, file.getBytes(), section.getChapterId(), lessonId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    (isHtml ? "HTML" : "PPT") + " 上传失败: " + e.getMessage());
        }
    }

    @Override
    public List<SlidePageVO> listSlidePages(Long courseId, Long lessonId) {
        requireSectionInCourse(courseId, lessonId);
        return slideService.getPages(courseId, lessonId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlidePageVO updateSlidePageNarration(Long courseId, Long lessonId, Integer pageNumber, Map<String, Object> body) {
        requireSectionInCourse(courseId, lessonId);
        Map<String, Object> payload = new HashMap<>(body);
        if (payload.containsKey("narrationScript")) {
            Object narration = payload.get("narrationScript");
            if (!(narration instanceof String)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "narrationScript 必须为字符串类型");
            }
            payload.put("narrationScript", XssSanitizer.sanitizePlainText((String) narration));
        }
        payload.put("_lessonId", lessonId);
        return slideService.updatePage(courseId, pageNumber, payload);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSlidePage(Long courseId, Long lessonId, Integer pageNumber) {
        requireSectionInCourse(courseId, lessonId);
        slideService.deletePage(courseId, pageNumber, lessonId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSectionCascade(Long courseId, Long sectionId) {
        CourseSection section = requireSectionInCourse(courseId, sectionId);
        deleteSectionCascadeInternal(courseId, section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapterCascade(Long courseId, Long chapterId) {
        requireChapterInCourse(courseId, chapterId);
        List<CourseSection> sections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>().eq(CourseSection::getChapterId, chapterId));
        for (CourseSection section : sections) {
            deleteSectionCascadeInternal(courseId, section);
        }
        chapterRepository.deleteById(chapterId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseCascade(Long courseId) {
        List<CourseSection> sections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>().eq(CourseSection::getCourseId, courseId));
        for (CourseSection section : sections) {
            deleteSectionCascadeInternal(courseId, section);
        }
        chapterRepository.delete(new LambdaQueryWrapper<CourseChapter>().eq(CourseChapter::getCourseId, courseId));
    }

    @Override
    public List<SlideVO> listSlides(Long courseId) {
        return slideService.listByCourseId(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchPushScripts(Long courseId, Long sectionId, Long chapterId, String scriptContent) {
        if (scriptContent == null || scriptContent.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 不能为空");
        }
        if (scriptContent.length() > 65535) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "scriptContent 不能超过 65535 字符");
        }
        if (sectionId != null) {
            requireSectionInCourse(courseId, sectionId);
        }

        String fullScript = XssSanitizer.sanitizePlainText(scriptContent);
        List<SlidePageVO> pages = slideService.getPages(courseId, sectionId);
        if (pages == null || pages.isEmpty()) {
            if (sectionId != null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课时无课件页面");
            }
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "请先上传课件");
        }

        int updated = 0;
        int pageCount = pages.size();
        if (sectionId != null) {
            for (SlidePageVO page : pages) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("narrationScript", fullScript);
                if (page.getSectionId() != null) {
                    payload.put("_lessonId", page.getSectionId());
                }
                slideService.updatePage(courseId, page.getPageNumber(), payload);
                updated++;
            }
        } else {
            if (chapterId == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "批量推送讲述稿时必须指定 chapterId 参数，以避免跨章节数据串写");
            }
            if (fullScript.length() < pageCount) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                        "讲述稿长度(" + fullScript.length() + ")少于课件页数(" + pageCount + ")，无法自动分配");
            }
            int chunkSize = fullScript.length() / pageCount;
            for (int i = 0; i < pageCount; i++) {
                int start = i * chunkSize;
                int end = (i == pageCount - 1) ? fullScript.length() : (i + 1) * chunkSize;
                Map<String, Object> payload = new HashMap<>();
                payload.put("narrationScript", fullScript.substring(start, end).trim());
                payload.put("_chapterId", chapterId);
                slideService.updatePage(courseId, pages.get(i).getPageNumber(), payload);
                updated++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("updated", updated);
        result.put("totalPages", pageCount);
        return result;
    }

    private void deleteSectionCascadeInternal(Long courseId, CourseSection section) {
        List<CourseSlide> slides = courseSlideMapper.selectList(
                new LambdaQueryWrapper<CourseSlide>().eq(CourseSlide::getSectionId, section.getId()));
        for (CourseSlide slide : slides) {
            slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, slide.getId()));
            courseSlideMapper.deleteById(slide.getId());
            registerSlideCleanup(courseId, slide.getId());
        }
        sectionRepository.deleteById(section.getId());
    }

    private void registerSlideCleanup(Long courseId, Long slideId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    slideService.cleanupSlideFiles(courseId, slideId);
                }
            });
            return;
        }
        slideService.cleanupSlideFiles(courseId, slideId);
    }

    private CourseSection requireSectionInCourse(Long courseId, Long sectionId) {
        CourseSection section = sectionRepository.selectById(sectionId);
        if (section == null) {
            throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        }
        if (!courseId.equals(section.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "课时不属于该课程");
        }
        return section;
    }

    private void requireChapterInCourse(Long courseId, Long chapterId) {
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        if (!courseId.equals(chapter.getCourseId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "章节不属于该课程");
        }
    }

    private String requireFilename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件名不能为空");
        }
        return filename;
    }

    private boolean isHtmlFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    private void validateUploadFile(MultipartFile file, String filename, boolean isHtml) {
        String lower = filename.toLowerCase();
        boolean isPptx = lower.endsWith(".pptx");
        if (!isHtml && !isPptx) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 .pptx / .html / .htm 文件");
        }
        long maxSize = isHtml ? 5 * 1024 * 1024 : 50 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    isHtml ? "HTML 文件大小不能超过 5MB" : "文件大小不能超过 50MB");
        }
    }
}

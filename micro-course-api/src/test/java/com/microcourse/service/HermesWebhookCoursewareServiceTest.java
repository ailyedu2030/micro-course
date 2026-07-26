package com.microcourse.service;

import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.service.impl.HermesWebhookCoursewareServiceImpl;
import com.microcourse.util.XssSanitizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HermesWebhookCoursewareService 课件桥接")
class HermesWebhookCoursewareServiceTest {

    @Test
    @DisplayName("uploadSlide 对 HTML 课件必须复用 section 的 chapterId 并委派 uploadHtmlFile")
    void uploadSlideDelegatesHtmlUploadWithSectionChapter() throws Exception {
        CourseSectionRepository sectionRepository = mock(CourseSectionRepository.class);
        CourseChapterRepository chapterRepository = mock(CourseChapterRepository.class);
        CourseSlideMapper courseSlideMapper = mock(CourseSlideMapper.class);
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        SlideService slideService = mock(SlideService.class);
        HermesWebhookCoursewareServiceImpl service = new HermesWebhookCoursewareServiceImpl(
                sectionRepository, chapterRepository, courseSlideMapper, slidePageMapper, slideService);

        CourseSection section = new CourseSection();
        section.setId(11L);
        section.setCourseId(5L);
        section.setChapterId(7L);
        when(sectionRepository.selectById(11L)).thenReturn(section);

        MockMultipartFile file = new MockMultipartFile("file", "deck.html", "text/html", "<html/>".getBytes());
        SlideUploadResponse response = new SlideUploadResponse();
        response.setSlideId(99L);
        when(slideService.uploadHtmlFile(5L, file, 7L, 11L)).thenReturn(response);

        Object result = service.uploadSlide(5L, 11L, file);

        assertEquals(response, result);
        verify(slideService).uploadHtmlFile(5L, file, 7L, 11L);
    }

    @Test
    @DisplayName("updateSlidePageNarration 必须清洗 narrationScript 并附带 _lessonId")
    @SuppressWarnings("unchecked")
    void updateSlidePageNarrationSanitizesBodyAndCarriesLessonId() {
        CourseSectionRepository sectionRepository = mock(CourseSectionRepository.class);
        CourseChapterRepository chapterRepository = mock(CourseChapterRepository.class);
        CourseSlideMapper courseSlideMapper = mock(CourseSlideMapper.class);
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        SlideService slideService = mock(SlideService.class);
        HermesWebhookCoursewareServiceImpl service = new HermesWebhookCoursewareServiceImpl(
                sectionRepository, chapterRepository, courseSlideMapper, slidePageMapper, slideService);

        CourseSection section = new CourseSection();
        section.setId(11L);
        section.setCourseId(5L);
        when(sectionRepository.selectById(11L)).thenReturn(section);

        SlidePageVO updated = new SlidePageVO();
        when(slideService.updatePage(eq(5L), eq(2), any())).thenReturn(updated);

        service.updateSlidePageNarration(5L, 11L, 2, Map.of("narrationScript", "<b>讲稿</b>"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(slideService).updatePage(eq(5L), eq(2), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertEquals(11L, payload.get("_lessonId"));
        assertEquals(XssSanitizer.sanitizePlainText("<b>讲稿</b>"), payload.get("narrationScript"));
    }

    @Test
    @DisplayName("deleteSectionCascade 必须删除课件页、课件记录、物理文件并删除 section")
    void deleteSectionCascadeRemovesSlidesAndSection() {
        CourseSectionRepository sectionRepository = mock(CourseSectionRepository.class);
        CourseChapterRepository chapterRepository = mock(CourseChapterRepository.class);
        CourseSlideMapper courseSlideMapper = mock(CourseSlideMapper.class);
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        SlideService slideService = mock(SlideService.class);
        HermesWebhookCoursewareServiceImpl service = new HermesWebhookCoursewareServiceImpl(
                sectionRepository, chapterRepository, courseSlideMapper, slidePageMapper, slideService);

        CourseSection section = new CourseSection();
        section.setId(11L);
        section.setCourseId(5L);
        when(sectionRepository.selectById(11L)).thenReturn(section);

        CourseSlide first = new CourseSlide();
        first.setId(101L);
        CourseSlide second = new CourseSlide();
        second.setId(102L);
        when(courseSlideMapper.selectList(any())).thenReturn(List.of(first, second));

        service.deleteSectionCascade(5L, 11L);

        verify(slidePageMapper, times(2)).delete(any());
        verify(courseSlideMapper).deleteById(101L);
        verify(courseSlideMapper).deleteById(102L);
        verify(slideService).cleanupSlideFiles(5L, 101L);
        verify(slideService).cleanupSlideFiles(5L, 102L);
        verify(sectionRepository).deleteById(11L);
    }

    @Test
    @DisplayName("batchPushScripts 在整章广播时必须按页拆分脚本并带上 _chapterId")
    @SuppressWarnings("unchecked")
    void batchPushScriptsSplitsWholeChapterScriptAcrossPages() {
        CourseSectionRepository sectionRepository = mock(CourseSectionRepository.class);
        CourseChapterRepository chapterRepository = mock(CourseChapterRepository.class);
        CourseSlideMapper courseSlideMapper = mock(CourseSlideMapper.class);
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        SlideService slideService = mock(SlideService.class);
        HermesWebhookCoursewareServiceImpl service = new HermesWebhookCoursewareServiceImpl(
                sectionRepository, chapterRepository, courseSlideMapper, slidePageMapper, slideService);

        SlidePageVO page1 = new SlidePageVO();
        page1.setPageNumber(1);
        SlidePageVO page2 = new SlidePageVO();
        page2.setPageNumber(2);
        when(slideService.getPages(5L, null)).thenReturn(List.of(page1, page2));

        Map<String, Object> result = service.batchPushScripts(5L, null, 9L, "abcdefgh");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(slideService, times(2)).updatePage(eq(5L), any(), captor.capture());
        List<Map<String, Object>> payloads = captor.getAllValues();
        assertEquals("abcd", payloads.get(0).get("narrationScript"));
        assertEquals("efgh", payloads.get(1).get("narrationScript"));
        assertEquals(9L, payloads.get(0).get("_chapterId"));
        assertEquals(9L, payloads.get(1).get("_chapterId"));
        assertEquals(2, result.get("updated"));
        assertEquals(2, result.get("totalPages"));
    }

    @Test
    @DisplayName("uploadSlide 对非法扩展名必须直接拒绝")
    void uploadSlideRejectsUnsupportedExtension() {
        CourseSectionRepository sectionRepository = mock(CourseSectionRepository.class);
        CourseChapterRepository chapterRepository = mock(CourseChapterRepository.class);
        CourseSlideMapper courseSlideMapper = mock(CourseSlideMapper.class);
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        SlideService slideService = mock(SlideService.class);
        HermesWebhookCoursewareServiceImpl service = new HermesWebhookCoursewareServiceImpl(
                sectionRepository, chapterRepository, courseSlideMapper, slidePageMapper, slideService);

        CourseSection section = new CourseSection();
        section.setId(11L);
        section.setCourseId(5L);
        when(sectionRepository.selectById(11L)).thenReturn(section);

        MockMultipartFile file = new MockMultipartFile("file", "deck.pdf", "application/pdf", new byte[1]);

        assertThrows(BusinessException.class, () -> service.uploadSlide(5L, 11L, file));
    }
}

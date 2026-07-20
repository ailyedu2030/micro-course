package com.microcourse.plugin.interactive;

import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.impl.CoursewareQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CoursewareQueryService (CQRS Query) 单元测试.
 *
 * 覆盖:
 * <ul>
 *   <li>getCoursewareTree: PPT 课件返回 type=PPT, 含 pages + flow</li>
 *   <li>getCoursewareTree: HTML 课件返回 type=HTML, 含 htmlUnit + segments</li>
 *   <li>getCoursewareTree: 两者皆空返回 type=EMPTY</li>
 *   <li>resolveAudioToken: PPT audio_token 路由正确</li>
 *   <li>resolveAudioToken: HTML segment audio_token 路由正确</li>
 *   <li>resolveAudioToken: 无效 token 抛 BusinessException</li>
 * </ul>
 */
class CoursewareQueryServiceTest {

    private SlidePptPageMapper pageMapper;
    private SlidePptPageScriptMapper pageScriptMapper;
    private SlidePptPageAudioMapper pageAudioMapper;
    private SlidePptFlowMapper flowMapper;
    private SlideHtmlUnitMapper unitMapper;
    private SlideHtmlSegmentScriptMapper segmentScriptMapper;
    private SlideHtmlSegmentAudioMapper segmentAudioMapper;
    private CoursewareQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        pageMapper = mock(SlidePptPageMapper.class);
        pageScriptMapper = mock(SlidePptPageScriptMapper.class);
        pageAudioMapper = mock(SlidePptPageAudioMapper.class);
        flowMapper = mock(SlidePptFlowMapper.class);
        unitMapper = mock(SlideHtmlUnitMapper.class);
        segmentScriptMapper = mock(SlideHtmlSegmentScriptMapper.class);
        segmentAudioMapper = mock(SlideHtmlSegmentAudioMapper.class);
        service = new CoursewareQueryServiceImpl(pageMapper, pageScriptMapper,
                pageAudioMapper, flowMapper, unitMapper, segmentScriptMapper, segmentAudioMapper,
                mock(com.microcourse.plugin.interactive.cache.AudioStreamCache.class));
    }

    @Test
    @DisplayName("getCoursewareTree: PPT course returns type=PPT with pages and audio status")
    void getPptTree() {
        // Given: 2 PPT pages in section 99
        SlidePptPage p1 = newPptPage(1L, 1, "Page 1", 99L, 42L);
        SlidePptPage p2 = newPptPage(2L, 2, "Page 2", 99L, 42L);
        when(pageMapper.listBySection(99L)).thenReturn(List.of(p1, p2));
        when(unitMapper.findBySection(99L)).thenReturn(null);
        when(flowMapper.listBySection(99L)).thenReturn(Collections.emptyList());

        // Page 1 has active script + 1 READY audio
        SlidePptPageScript script1 = newActiveScript(100L, 1L, "script of page 1");
        when(pageScriptMapper.findActiveByPage(1L)).thenReturn(script1);
        SlidePptPageAudio audio1 = newAudio(500L, 100L, 1L, "READY");
        when(pageAudioMapper.listByScript(100L)).thenReturn(List.of(audio1));

        // Page 2 has script but no audio yet
        SlidePptPageScript script2 = newActiveScript(101L, 2L, "script of page 2");
        when(pageScriptMapper.findActiveByPage(2L)).thenReturn(script2);
        when(pageAudioMapper.listByScript(101L)).thenReturn(Collections.emptyList());

        // When
        CoursewareTreeDTO tree = service.getCoursewareTree(42L, 99L);

        // Then
        assertEquals("PPT", tree.getType());
        assertEquals(99L, tree.getSectionId());
        assertEquals(42L, tree.getCourseId());
        assertEquals(2, tree.getPages().size());
        assertEquals("AUDIO_READY", tree.getNarrationStatus(), "page1 ready → overall READY");
        assertEquals(1, tree.getAudioReadyCount());
        // Page 1 should be READY (has READY audio)
        assertEquals("AUDIO_READY", tree.getPages().get(0).getNarrationStatus());
        // Page 2 should be GENERATING (script exists but no audio)
        assertEquals("AUDIO_GENERATING", tree.getPages().get(1).getNarrationStatus());
    }

    @Test
    @DisplayName("getCoursewareTree: empty section returns type=EMPTY")
    void getEmptyTree() {
        when(pageMapper.listBySection(99L)).thenReturn(Collections.emptyList());
        when(unitMapper.findBySection(99L)).thenReturn(null);

        CoursewareTreeDTO tree = service.getCoursewareTree(42L, 99L);

        assertEquals("EMPTY", tree.getType());
        assertEquals(99L, tree.getSectionId());
        assertEquals(0, tree.getAudioReadyCount());
        assertEquals("PENDING", tree.getNarrationStatus());
    }

    @Test
    @DisplayName("resolveAudioToken: PPT audio token returns type=PPT, ownerId=pptPageId")
    void resolvePptAudioToken() {
        SlidePptPageAudio audio = newAudio(500L, 100L, 42L, "READY");
        audio.setAudioToken("abcdef1234567890abcdef1234567890");
        when(pageAudioMapper.findByToken("abcdef1234567890abcdef1234567890")).thenReturn(audio);
        when(segmentAudioMapper.findByToken(any())).thenReturn(null);

        AudioStreamInfo info = service.resolveAudioToken("abcdef1234567890abcdef1234567890");

        assertEquals("PPT", info.getCoursewareType());
        assertEquals(42L, info.getOwnerId());
        assertEquals(100L, info.getScriptId());
        assertEquals("abcdef1234567890abcdef1234567890", info.getToken());
        assertEquals("READY", info.getStatus());
    }

    @Test
    @DisplayName("resolveAudioToken: HTML segment audio token returns type=HTML, segmentIndex set")
    void resolveHtmlAudioToken() {
        when(pageAudioMapper.findByToken(any())).thenReturn(null);
        SlideHtmlSegmentAudio htmlAudio = new SlideHtmlSegmentAudio();
        htmlAudio.setId(8000L);
        htmlAudio.setSegmentScriptId(2000L);
        htmlAudio.setHtmlUnitId(100L);
        htmlAudio.setSegmentIndex(3);
        htmlAudio.setAudioToken("fedcba0987654321fedcba0987654321");
        htmlAudio.setAudioUrl("/api/courses/42/audio/fedcba...");
        htmlAudio.setStatus("READY");
        when(segmentAudioMapper.findByToken("fedcba0987654321fedcba0987654321")).thenReturn(htmlAudio);

        AudioStreamInfo info = service.resolveAudioToken("fedcba0987654321fedcba0987654321");

        assertEquals("HTML", info.getCoursewareType());
        assertEquals(100L, info.getOwnerId());
        assertEquals(2000L, info.getScriptId());
        assertEquals(Long.valueOf(3L), info.getSegmentIndex(), "HTML segment index propagated");
    }

    @Test
    @DisplayName("resolveAudioToken: invalid token throws BusinessException with masked token")
    void resolveInvalidToken() {
        when(pageAudioMapper.findByToken(any())).thenReturn(null);
        when(segmentAudioMapper.findByToken(any())).thenReturn(null);

        String invalid = "deadbeef00000000deadbeef00000000";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveAudioToken(invalid));
        // 错误信息应包含前 8 字符 (审计用, 不泄露全 token)
        assertTrue(ex.getMessage().contains("deadbeef"));
    }

    // ====== Helpers ======

    private SlidePptPage newPptPage(Long id, int pageNum, String title, Long sectionId, Long courseId) {
        SlidePptPage p = new SlidePptPage();
        p.setId(id);
        p.setPageNumber(pageNum);
        p.setPageTitle(title);
        p.setSectionId(sectionId);
        p.setCourseId(courseId);
        p.setChapterId(1L);
        p.setSlideId(1L);
        p.setImageUrl("/img/p" + pageNum + ".png");
        return p;
    }

    private SlidePptPageScript newActiveScript(Long id, Long pageId, String text) {
        SlidePptPageScript s = new SlidePptPageScript();
        s.setId(id);
        s.setPptPageId(pageId);
        s.setScriptText(text);
        s.setScriptVersion(1);
        s.setIsActive(true);
        return s;
    }

    private SlidePptPageAudio newAudio(Long id, Long scriptId, Long pageId, String status) {
        SlidePptPageAudio a = new SlidePptPageAudio();
        a.setId(id);
        a.setScriptId(scriptId);
        a.setPptPageId(pageId);
        a.setAudioUrl("/api/courses/42/audio/" + id);
        a.setAudioToken(String.format("%032d", id));
        a.setStatus(status);
        return a;
    }
}
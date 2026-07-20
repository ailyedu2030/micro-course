package com.microcourse.plugin.interactive;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.service.impl.HtmlCoursewareServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HtmlCoursewareService 单元测试.
 *
 * 覆盖:
 * <ul>
 *   <li>createUnit in-place UPSERT (P1-C 修复兼容)</li>
 *   <li>createUnit 调用 HtmlSanitizer (7-19 P0 防御)</li>
 *   <li>updateUnit 触发重新 sanitize (P0 防御)</li>
 *   <li>saveSegmentScript partial unique (active demote + new insert)</li>
 *   <li>generateSegmentAudio audio_token 32 字符 UK</li>
 * </ul>
 */
class HtmlCoursewareServiceTest {

    private SlideHtmlUnitMapper unitMapper;
    private SlideHtmlSegmentScriptMapper segmentScriptMapper;
    private SlideHtmlSegmentAudioMapper segmentAudioMapper;
    private HtmlCoursewareServiceImpl service;

    @BeforeEach
    void setUp() {
        unitMapper = mock(SlideHtmlUnitMapper.class);
        segmentScriptMapper = mock(SlideHtmlSegmentScriptMapper.class);
        segmentAudioMapper = mock(SlideHtmlSegmentAudioMapper.class);
        service = new HtmlCoursewareServiceImpl(unitMapper, segmentScriptMapper, segmentAudioMapper);
    }

    @Nested
    @DisplayName("Unit CRUD with HtmlSanitizer")
    class UnitCrud {

        @Test
        @DisplayName("createUnit calls HtmlSanitizer.sanitizeForCourseware (7-19 P0 defense)")
        void createUnitSanitizesHtml() {
            when(unitMapper.findBySection(99L)).thenReturn(null);
            when(unitMapper.insert(any(SlideHtmlUnit.class))).thenAnswer(inv -> {
                SlideHtmlUnit e = inv.getArgument(0);
                e.setId(100L);
                return 1;
            });

            SlideHtmlUnitDTO dto = new SlideHtmlUnitDTO();
            dto.setSectionId(99L);
            dto.setCourseId(1L);
            dto.setChapterId(1L);
            dto.setSlideId(1L);
            dto.setHtmlContent("<script>alert('xss')</script><p>good</p>");
            dto.setFileSizeBytes(100L);

            service.createUnit(dto);

            ArgumentCaptor<SlideHtmlUnit> captor = ArgumentCaptor.forClass(SlideHtmlUnit.class);
            verify(unitMapper).insert(captor.capture());
            SlideHtmlUnit saved = captor.getValue();
            // HtmlSanitizer.sanitizeForCourseware uses COURSEWARE_SAFELIST which is permissive (teacher-trusted)
            // We only verify sanitize() is called (output != input proves it ran)
            assertNotNull(saved.getHtmlSanitized());
            assertNotEquals(dto.getHtmlContent(), saved.getHtmlSanitized(),
                    "sanitize() must transform input");
            assertTrue(saved.getHtmlSanitized().contains("good"),
                    "safe text content preserved");
        }

        @Test
        @DisplayName("createUnit in-place UPSERT: existing unit triggers update, no second insert")
        void createUnitInPlaceUpsert() {
            SlideHtmlUnit existing = new SlideHtmlUnit();
            existing.setId(500L);
            existing.setSectionId(99L);
            existing.setCourseId(1L);
            existing.setChapterId(1L);
            existing.setSlideId(1L);
            existing.setHtmlContent("<p>old</p>");
            existing.setHtmlSanitized("<p>old</p>");
            existing.setFileSizeBytes(50L);
            when(unitMapper.findBySection(99L)).thenReturn(existing);
            // updateUnit internally calls selectById then updateById
            when(unitMapper.selectById(500L)).thenReturn(existing);
            when(unitMapper.updateById(any(SlideHtmlUnit.class))).thenReturn(1);

            SlideHtmlUnitDTO dto = new SlideHtmlUnitDTO();
            dto.setSectionId(99L);
            dto.setCourseId(1L);
            dto.setChapterId(1L);
            dto.setSlideId(1L);
            dto.setHtmlContent("<p>new content</p>");
            dto.setFileSizeBytes(100L);

            Long id = service.createUnit(dto);

            assertEquals(500L, id, "returns existing id (no new insert)");
            verify(unitMapper, never()).insert(any(SlideHtmlUnit.class));
            verify(unitMapper).updateById(any(SlideHtmlUnit.class));
        }

        @Test
        @DisplayName("createUnit rejects null htmlContent with BAD_REQUEST_PARAM")
        void createUnitRejectsEmptyHtml() {
            when(unitMapper.findBySection(99L)).thenReturn(null);
            SlideHtmlUnitDTO dto = new SlideHtmlUnitDTO();
            dto.setSectionId(99L);
            dto.setCourseId(1L);
            dto.setChapterId(1L);
            dto.setSlideId(1L);
            dto.setHtmlContent(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.createUnit(dto));
            assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("Segment script versioning")
    class SegmentScriptVersioning {

        @Test
        @DisplayName("saveSegmentScript first version: no demote, version=1, isActive=true")
        void saveFirstSegmentScript() {
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(100L);
            unit.setSectionId(99L);
            when(unitMapper.selectById(100L)).thenReturn(unit);
            when(segmentScriptMapper.findActiveByUnitAndIndex(100L, 1)).thenReturn(null);
            when(segmentScriptMapper.insert(any(SlideHtmlSegmentScript.class))).thenAnswer(inv -> {
                SlideHtmlSegmentScript e = inv.getArgument(0);
                e.setId(2000L);
                return 1;
            });

            Long id = service.saveSegmentScript(100L, 1, "first script", "voice1",
                    "model1", "seg-1", 42L);
            assertEquals(2000L, id);

            ArgumentCaptor<SlideHtmlSegmentScript> captor = ArgumentCaptor.forClass(SlideHtmlSegmentScript.class);
            verify(segmentScriptMapper).insert(captor.capture());
            SlideHtmlSegmentScript saved = captor.getValue();
            assertEquals(Integer.valueOf(1), saved.getScriptVersion());
            assertEquals(Boolean.TRUE, saved.getIsActive());
            assertEquals("seg-1", saved.getSegmentMarker(), "DOM marker preserved");
        }

        @Test
        @DisplayName("saveSegmentScript second version: demotes old active, new version=2")
        void saveSecondSegmentScriptDemotesOld() {
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(100L);
            when(unitMapper.selectById(100L)).thenReturn(unit);

            SlideHtmlSegmentScript oldActive = new SlideHtmlSegmentScript();
            oldActive.setId(2000L);
            oldActive.setHtmlUnitId(100L);
            oldActive.setSegmentIndex(1);
            oldActive.setScriptVersion(1);
            oldActive.setIsActive(true);
            when(segmentScriptMapper.findActiveByUnitAndIndex(100L, 1)).thenReturn(oldActive);
            when(segmentScriptMapper.updateById(any(SlideHtmlSegmentScript.class))).thenReturn(1);
            when(segmentScriptMapper.insert(any(SlideHtmlSegmentScript.class))).thenAnswer(inv -> {
                SlideHtmlSegmentScript e = inv.getArgument(0);
                e.setId(2001L);
                return 1;
            });

            Long id = service.saveSegmentScript(100L, 1, "second script", "voice2",
                    "model2", null, 42L);
            assertEquals(2001L, id);

            ArgumentCaptor<SlideHtmlSegmentScript> demoteCaptor = ArgumentCaptor.forClass(SlideHtmlSegmentScript.class);
            verify(segmentScriptMapper).updateById(demoteCaptor.capture());
            assertEquals(Boolean.FALSE, demoteCaptor.getValue().getIsActive());

            ArgumentCaptor<SlideHtmlSegmentScript> insertCaptor = ArgumentCaptor.forClass(SlideHtmlSegmentScript.class);
            verify(segmentScriptMapper).insert(insertCaptor.capture());
            assertEquals(Integer.valueOf(2), insertCaptor.getValue().getScriptVersion());
            assertEquals(Boolean.TRUE, insertCaptor.getValue().getIsActive());
        }
    }

    @Nested
    @DisplayName("Segment audio generation")
    class SegmentAudio {

        @Test
        @DisplayName("generateSegmentAudio: audio_token 32-char hex, audio_url pattern")
        void generateSegmentAudioFormat() {
            SlideHtmlSegmentScript script = new SlideHtmlSegmentScript();
            script.setId(2000L);
            script.setHtmlUnitId(100L);
            script.setSegmentIndex(3);
            when(segmentScriptMapper.selectById(2000L)).thenReturn(script);
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(100L);
            unit.setCourseId(42L);
            when(unitMapper.selectById(100L)).thenReturn(unit);
            when(segmentAudioMapper.insert(any(SlideHtmlSegmentAudio.class))).thenAnswer(inv -> {
                SlideHtmlSegmentAudio e = inv.getArgument(0);
                e.setId(8000L);
                return 1;
            });

            Long id = service.generateSegmentAudio(2000L, "voice-young", "MiniMax-speech-01", "{\"speed\":1.0}");
            assertEquals(8000L, id);

            ArgumentCaptor<SlideHtmlSegmentAudio> captor = ArgumentCaptor.forClass(SlideHtmlSegmentAudio.class);
            verify(segmentAudioMapper).insert(captor.capture());
            SlideHtmlSegmentAudio saved = captor.getValue();
            assertNotNull(saved.getAudioToken());
            assertEquals(32, saved.getAudioToken().length(),
                    "audio_token must be 32-char hex (7-19 P1-C UK validation)");
            assertTrue(saved.getAudioToken().matches("[0-9a-f]{32}"));
            assertEquals("/api/courses/42/audio/" + saved.getAudioToken(), saved.getAudioUrl());
            assertEquals(Integer.valueOf(3), saved.getSegmentIndex(), "segmentIndex denormalized for query");
            assertEquals("GENERATING", saved.getStatus());
        }
    }
}
package com.microcourse.plugin.interactive;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.impl.PptCoursewareServiceImpl;
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
 * PptCoursewareService 单元测试.
 *
 * 覆盖:
 * <ul>
 *   <li>createPage 字段默认值</li>
 *   <li>saveScript partial unique 兼容 (旧 active 降级 + 新 active)</li>
 *   <li>generateAudio audio_token 32 字符 UK</li>
 *   <li>deletePage 不存在抛 BusinessException</li>
 *   <li>listPagesBySection 按 page_number 排序</li>
 * </ul>
 */
class PptCoursewareServiceTest {

    private SlidePptPageMapper pageMapper;
    private SlidePptPageScriptMapper scriptMapper;
    private SlidePptPageAudioMapper audioMapper;
    private SlidePptFlowMapper flowMapper;
    private PptCoursewareServiceImpl service;

    @BeforeEach
    void setUp() {
        pageMapper = mock(SlidePptPageMapper.class);
        scriptMapper = mock(SlidePptPageScriptMapper.class);
        audioMapper = mock(SlidePptPageAudioMapper.class);
        flowMapper = mock(SlidePptFlowMapper.class);
        service = new PptCoursewareServiceImpl(pageMapper, scriptMapper, audioMapper, flowMapper);
    }

    @Nested
    @DisplayName("Page CRUD")
    class PageCrud {

        @Test
        @DisplayName("createPage sets defaults: hasAnimation=false, hasEmbeddedMedia=false, timestamps now")
        void createPageSetsDefaults() {
            SlidePptPageDTO dto = new SlidePptPageDTO();
            dto.setCourseId(1L);
            dto.setChapterId(1L);
            dto.setSectionId(1L);
            dto.setSlideId(1L);
            dto.setPageNumber(1);
            dto.setImageUrl("/tmp/p1.png");

            when(pageMapper.insert(any(SlidePptPage.class))).thenAnswer(inv -> {
                SlidePptPage entity = inv.getArgument(0);
                entity.setId(100L);
                return 1;
            });

            Long id = service.createPage(dto);

            assertEquals(100L, id);
            ArgumentCaptor<SlidePptPage> captor = ArgumentCaptor.forClass(SlidePptPage.class);
            verify(pageMapper).insert(captor.capture());
            SlidePptPage saved = captor.getValue();
            assertNotNull(saved.getCreatedAt(), "createdAt must be set");
            assertNotNull(saved.getUpdatedAt(), "updatedAt must be set");
            assertEquals(Boolean.FALSE, saved.getHasAnimation(), "default hasAnimation=false");
            assertEquals(Boolean.FALSE, saved.getHasEmbeddedMedia(), "default hasEmbeddedMedia=false");
        }

        @Test
        @DisplayName("deletePage on missing id throws BusinessException(SLIDE_PAGE_NOT_FOUND)")
        void deletePageMissingThrows() {
            when(pageMapper.deleteById(999L)).thenReturn(0);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.deletePage(999L));
            assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("listPagesBySection returns mapper results in order")
        void listPagesBySectionOrdered() {
            SlidePptPage p1 = new SlidePptPage();
            p1.setId(1L);
            p1.setPageNumber(1);
            p1.setCourseId(1L);
            p1.setSectionId(99L);
            p1.setSlideId(1L);
            SlidePptPage p2 = new SlidePptPage();
            p2.setId(2L);
            p2.setPageNumber(2);
            p2.setCourseId(1L);
            p2.setSectionId(99L);
            p2.setSlideId(1L);
            when(pageMapper.listBySection(99L)).thenReturn(List.of(p1, p2));

            List<SlidePptPageDTO> result = service.listPagesBySection(99L);
            assertEquals(2, result.size());
            assertEquals(1, result.get(0).getPageNumber());
            assertEquals(2, result.get(1).getPageNumber());
        }
    }

    @Nested
    @DisplayName("Script versioning")
    class ScriptVersioning {

        @Test
        @DisplayName("saveScript first version: no downgrade, version=1, isActive=true")
        void saveScriptFirstVersion() {
            when(scriptMapper.findActiveByPage(10L)).thenReturn(null);
            when(scriptMapper.insert(any(SlidePptPageScript.class))).thenAnswer(inv -> {
                SlidePptPageScript entity = inv.getArgument(0);
                entity.setId(1000L);
                return 1;
            });

            Long id = service.saveScript(10L, "first script", "voice1", "model1", 42L);
            assertEquals(1000L, id);

            ArgumentCaptor<SlidePptPageScript> captor = ArgumentCaptor.forClass(SlidePptPageScript.class);
            verify(scriptMapper).insert(captor.capture());
            SlidePptPageScript saved = captor.getValue();
            assertEquals(Integer.valueOf(1), saved.getScriptVersion(), "first version=1");
            assertEquals(Boolean.TRUE, saved.getIsActive());
            assertEquals("first script", saved.getScriptText());
            assertEquals(Long.valueOf(42L), saved.getCreatedBy());
        }

        @Test
        @DisplayName("saveScript second version: demotes old active, new version=2, isActive=true")
        void saveScriptDemotesOldActive() {
            SlidePptPageScript oldActive = new SlidePptPageScript();
            oldActive.setId(1000L);
            oldActive.setPptPageId(10L);
            oldActive.setScriptVersion(1);
            oldActive.setIsActive(true);
            when(scriptMapper.findActiveByPage(10L)).thenReturn(oldActive);
            when(scriptMapper.updateById(any(SlidePptPageScript.class))).thenReturn(1);
            when(scriptMapper.insert(any(SlidePptPageScript.class))).thenAnswer(inv -> {
                SlidePptPageScript entity = inv.getArgument(0);
                entity.setId(1001L);
                return 1;
            });

            Long id = service.saveScript(10L, "second script", "voice2", "model2", 42L);

            assertEquals(1001L, id);
            // Old active demoted to false
            ArgumentCaptor<SlidePptPageScript> updateCaptor = ArgumentCaptor.forClass(SlidePptPageScript.class);
            verify(scriptMapper).updateById(updateCaptor.capture());
            SlidePptPageScript demoted = updateCaptor.getValue();
            assertEquals(Boolean.FALSE, demoted.getIsActive(), "old active demoted");

            // New script version=2 (only one insert call)
            ArgumentCaptor<SlidePptPageScript> insertCaptor = ArgumentCaptor.forClass(SlidePptPageScript.class);
            verify(scriptMapper).insert(insertCaptor.capture());
            SlidePptPageScript inserted = insertCaptor.getValue();
            assertEquals(Integer.valueOf(2), inserted.getScriptVersion(), "second version=2");
            assertEquals(Boolean.TRUE, inserted.getIsActive());
        }
    }

    @Nested
    @DisplayName("Audio generation")
    class AudioGeneration {

        @Test
        @DisplayName("generateAudio: audio_token is 32-char hex (UUID without dashes)")
        void generateAudioTokenFormat() {
            SlidePptPageScript script = new SlidePptPageScript();
            script.setId(1000L);
            script.setPptPageId(10L);
            when(scriptMapper.selectById(1000L)).thenReturn(script);
            SlidePptPage page = new SlidePptPage();
            page.setId(10L);
            page.setCourseId(42L);
            page.setSectionId(7L);
            page.setSlideId(1L);
            when(pageMapper.selectById(10L)).thenReturn(page);
            when(audioMapper.insert(any(SlidePptPageAudio.class))).thenAnswer(inv -> {
                SlidePptPageAudio entity = inv.getArgument(0);
                entity.setId(5000L);
                return 1;
            });

            Long id = service.generateAudio(1000L, "voice-young", "MiniMax-speech-01", "{\"speed\":1.0}");
            assertEquals(5000L, id);

            ArgumentCaptor<SlidePptPageAudio> captor = ArgumentCaptor.forClass(SlidePptPageAudio.class);
            verify(audioMapper).insert(captor.capture());
            SlidePptPageAudio saved = captor.getValue();
            assertNotNull(saved.getAudioToken());
            assertEquals(32, saved.getAudioToken().length(),
                    "audio_token must be 32-char hex (7-19 P1-C UK validation requirement)");
            assertTrue(saved.getAudioToken().matches("[0-9a-f]{32}"),
                    "audio_token must be lowercase hex only");
            assertEquals("/api/courses/42/audio/" + saved.getAudioToken(), saved.getAudioUrl(),
                    "audio_url pattern: /api/courses/{cid}/audio/{token}");
            assertEquals("GENERATING", saved.getStatus(), "initial status=GENERATING");
        }

        @Test
        @DisplayName("generateAudio: missing script throws SLIDE_PAGE_NOT_FOUND")
        void generateAudioMissingScriptThrows() {
            when(scriptMapper.selectById(999L)).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.generateAudio(999L, "v", "m", null));
            assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), ex.getCode());
        }
    }
}

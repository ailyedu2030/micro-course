package com.microcourse.plugin.interactive;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.service.impl.SlideRenderService;
import com.microcourse.plugin.interactive.service.impl.SlideServiceImpl;
import com.microcourse.plugin.interactive.service.HtmlSegmentDetector;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SlideServiceTest {

    private CourseSlideMapper courseSlideMapper;
    private SlidePageMapper slidePageMapper;
    private CourseRepository courseRepository;
    private CourseChapterRepository courseChapterRepository;
    private CourseSectionRepository courseSectionRepository;
    private SlideRenderService slideRenderService;
    private SlidePptPageMapper pptPageMapper;
    private SlidePptPageScriptMapper pptScriptMapper;
    private SlidePptPageAudioMapper pptAudioMapper;
    private SlidePptFlowMapper pptFlowMapper;
    private SlideHtmlUnitMapper htmlUnitMapper;
    private SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;
    private SlideHtmlSegmentAudioMapper htmlSegmentAudioMapper;
    private HtmlSegmentDetector htmlSegmentDetector;
    private SlideServiceImpl slideService;

    @BeforeEach
    void setUp() {
        courseSlideMapper = mock(CourseSlideMapper.class);
        slidePageMapper = mock(SlidePageMapper.class);
        courseRepository = mock(CourseRepository.class);
        courseChapterRepository = mock(CourseChapterRepository.class);
        courseSectionRepository = mock(CourseSectionRepository.class);
        slideRenderService = mock(SlideRenderService.class);
        pptPageMapper = mock(SlidePptPageMapper.class);
        pptScriptMapper = mock(SlidePptPageScriptMapper.class);
        pptAudioMapper = mock(SlidePptPageAudioMapper.class);
        pptFlowMapper = mock(SlidePptFlowMapper.class);
        htmlUnitMapper = mock(SlideHtmlUnitMapper.class);
        htmlSegmentScriptMapper = mock(SlideHtmlSegmentScriptMapper.class);
        htmlSegmentAudioMapper = mock(SlideHtmlSegmentAudioMapper.class);
        htmlSegmentDetector = mock(HtmlSegmentDetector.class);
        io.micrometer.core.instrument.MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        slideService = new SlideServiceImpl(courseSlideMapper, slidePageMapper, courseRepository,
                courseChapterRepository, courseSectionRepository, slideRenderService,
                pptPageMapper, pptScriptMapper, pptAudioMapper, pptFlowMapper,
                htmlUnitMapper, htmlSegmentScriptMapper, htmlSegmentAudioMapper,
                mock(com.microcourse.plugin.interactive.cache.CoursewarePagesCache.class),
                htmlSegmentDetector, meterRegistry);
        ReflectionTestUtils.setField(slideService, "storagePath", "/tmp/slides-test");
        ReflectionTestUtils.setField(slideService, "maxHtmlSize", 5L * 1024 * 1024);
    }

    @Nested
    @DisplayName("查询课件")
    class GetByCourse {
        @Test
        @DisplayName("存在时返回 SlideVO")
        void getByCourseId_Found() {
            CourseSlide slide = new CourseSlide();
            slide.setId(1L);
            slide.setCourseId(1L);
            slide.setFileName("test.pptx");
            slide.setTotalPages(10);
            slide.setStatus(2);
            when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(slide));

            SlideVO vo = slideService.getByCourseId(1L);
            assertNotNull(vo);
            assertEquals(1L, vo.getId());
            assertEquals(10, vo.getTotalPages());
            assertEquals(2, vo.getStatus());
        }

        @Test
        @DisplayName("不存在时返回 null")
        void getByCourseId_NotFound() {
            when(courseSlideMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            assertNull(slideService.getByCourseId(999L));
        }
    }

    @Nested
    @DisplayName("获取页面列表")
    class GetPages {
        @Test
        @DisplayName("返回排序后的页面列表")
        void getPages_ReturnsSorted() {
            CourseSlide slide = new CourseSlide();
            slide.setId(1L);
            slide.setCourseId(1L);
            slide.setStatus(2);
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(slide);

            SlidePage p1 = new SlidePage(); p1.setId(1L); p1.setPageNumber(1); p1.setSlideId(1L);
            p1.setCourseId(1L); p1.setNarrationStatus("PENDING");
            SlidePage p2 = new SlidePage(); p2.setId(2L); p2.setPageNumber(2); p2.setSlideId(1L);
            p2.setCourseId(1L); p2.setNarrationStatus("AI_GENERATED");
            when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p1, p2));

            List<SlidePageVO> pages = slideService.getPages(1L, null, null);
            assertEquals(2, pages.size());
            assertEquals(1, pages.get(0).getPageNumber());
            assertEquals("PENDING", pages.get(0).getNarrationStatus());
        }

        @Test
        @DisplayName("无课件时返回空列表")
        void getPages_NoSlide() {
            when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            assertTrue(slideService.getPages(1L, null, null).isEmpty());
        }
    }

    @Nested
    @DisplayName("P0 v2 聚合")
    class GetPagesV2 {
        @Test
        @DisplayName("section 有 v2 PPT 页时返回聚合 VO（audio + flows）")
        void getPages_V2PptAggregates() {
            SlidePptPage page = new SlidePptPage();
            page.setId(10L); page.setCourseId(1L); page.setSectionId(5L);
            page.setPageNumber(1); page.setSlideId(2L);
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of(page));
            SlidePptPageScript script = new SlidePptPageScript();
            script.setId(3L); script.setPptPageId(10L); script.setScriptText("讲述稿");
            // Q-2: 批量查询（N+1 修复）——1 SQL 取全部 active scripts
            when(pptScriptMapper.listActiveByPageIds(List.of(10L))).thenReturn(List.of(script));
            SlidePptPageAudio audio = new SlidePptPageAudio();
            audio.setId(4L); audio.setScriptId(3L); audio.setStatus("READY");
            audio.setAudioToken("tok123"); audio.setAudioDurationMs(30000);
            // Q-2: 批量查询——1 SQL 取全部 audios
            when(pptAudioMapper.listByScriptIds(List.of(3L))).thenReturn(List.of(audio));
            SlidePptFlow flow = new SlidePptFlow();
            flow.setFromPageId(10L); flow.setToPageId(11L); flow.setFlowType("NEXT");
            when(pptFlowMapper.listBySection(5L)).thenReturn(List.of(flow));

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);
            assertEquals(1, pages.size());
            SlidePageVO vo = pages.get(0);
            assertEquals("PPT_RENDERED", vo.getContentType());
            assertNotNull(vo.getAudio());
            assertEquals("/api/courses/1/courseware/audio/tok123", vo.getAudio().getUrl());
            assertEquals("AUDIO_READY", vo.getNarrationStatus());
            assertEquals(1, vo.getFlows().size());
            assertEquals("NEXT", vo.getFlows().get(0).getFlowType());
        }

        @Test
        @DisplayName("section 有 v2 HTML unit 时返回分段 VO")
        void getPages_V2HtmlAggregates() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<h1>t</h1>"); unit.setDetectedSegments(1);
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1); seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            SlideHtmlSegmentAudio segAudio = new SlideHtmlSegmentAudio();
            segAudio.setId(7L); segAudio.setSegmentScriptId(6L); segAudio.setStatus("READY");
            segAudio.setAudioToken("tok-html"); segAudio.setAudioDurationMs(15000);
            // Q-2: 批量查询——1 SQL 取全部段 audios
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of(segAudio));

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);
            assertEquals(1, pages.size());
            SlidePageVO vo = pages.get(0);
            assertEquals("HTML_DIRECT", vo.getContentType());
            assertNotNull(vo.getSegments());
            assertEquals(1, vo.getSegments().size());
            assertEquals("AUDIO_READY", vo.getNarrationStatus());
        }

        @Test
        @DisplayName("P2: HTML 段注入 data-segment + bridge.js（读时增强）")
        void getPages_V2HtmlInjectsSegmentBridge() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<html><body><h1 id=\"seg-1\">第一段</h1><p>内容A</p></body></html>");
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1); seg.setSegmentMarker("seg-1"); seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            SlideHtmlSegmentAudio segAudio = new SlideHtmlSegmentAudio();
            segAudio.setId(7L); segAudio.setSegmentScriptId(6L); segAudio.setStatus("READY");
            segAudio.setAudioToken("tok-html"); segAudio.setAudioDurationMs(15000);
            // Q-2: 批量查询——1 SQL 取全部段 audios
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of(segAudio));

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);
            String html = pages.get(0).getHtmlContent();
            assertTrue(html.contains("data-segment=\"1\""));
            assertTrue(html.contains("slide-audio-v2"));
            assertTrue(html.contains("segment-activated"));
        }

        @Test
        @DisplayName("Q-2: v2 PPT 聚合批量查询（1 次 listActiveByPageIds + 1 次 listByScriptIds，无 N+1）")
        void getPages_V2PptNoNplus1() {
            SlidePptPage p1 = new SlidePptPage();
            p1.setId(10L); p1.setCourseId(1L); p1.setSectionId(5L);
            p1.setPageNumber(1); p1.setSlideId(2L);
            SlidePptPage p2 = new SlidePptPage();
            p2.setId(11L); p2.setCourseId(1L); p2.setSectionId(5L);
            p2.setPageNumber(2); p2.setSlideId(2L);
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of(p1, p2));
            SlidePptPageScript s1 = new SlidePptPageScript();
            s1.setId(3L); s1.setPptPageId(10L); s1.setScriptText("脚本1");
            SlidePptPageScript s2 = new SlidePptPageScript();
            s2.setId(4L); s2.setPptPageId(11L); s2.setScriptText("脚本2");
            when(pptScriptMapper.listActiveByPageIds(List.of(10L, 11L))).thenReturn(List.of(s1, s2));
            SlidePptPageAudio a1 = new SlidePptPageAudio();
            a1.setId(1L); a1.setScriptId(3L); a1.setStatus("READY");
            a1.setAudioToken("tok1"); a1.setAudioDurationMs(10000);
            SlidePptPageAudio a2 = new SlidePptPageAudio();
            a2.setId(2L); a2.setScriptId(4L); a2.setStatus("READY");
            a2.setAudioToken("tok2"); a2.setAudioDurationMs(20000);
            when(pptAudioMapper.listByScriptIds(List.of(3L, 4L))).thenReturn(List.of(a1, a2));
            when(pptFlowMapper.listBySection(5L)).thenReturn(List.of());

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);

            assertEquals(2, pages.size());
            assertEquals("AUDIO_READY", pages.get(0).getNarrationStatus());
            // N+1 断言：批量方法各只调用 1 次；逐页查询方法绝不调用
            verify(pptScriptMapper).listActiveByPageIds(anyList());
            verify(pptAudioMapper).listByScriptIds(anyList());
            verify(pptScriptMapper, never()).findActiveByPage(anyLong());
            verify(pptAudioMapper, never()).listByScript(anyLong());
            verify(htmlSegmentAudioMapper, never()).listByScript(anyLong());
        }

        @Test
        @DisplayName("Q-2: v2 HTML 段音频批量查询（1 次 listByScriptIds，无 N+1）")
        void getPages_V2HtmlNoNplus1() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<p>hello</p>"); unit.setIsTrusted(Boolean.TRUE);
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg1 = new SlideHtmlSegmentScript();
            seg1.setId(6L); seg1.setSegmentIndex(1); seg1.setScriptText("段1");
            SlideHtmlSegmentScript seg2 = new SlideHtmlSegmentScript();
            seg2.setId(7L); seg2.setSegmentIndex(2); seg2.setScriptText("段2");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg1, seg2));
            SlideHtmlSegmentAudio sa1 = new SlideHtmlSegmentAudio();
            sa1.setId(1L); sa1.setSegmentScriptId(6L); sa1.setStatus("READY");
            sa1.setAudioToken("tok-h1"); sa1.setAudioDurationMs(10000);
            SlideHtmlSegmentAudio sa2 = new SlideHtmlSegmentAudio();
            sa2.setId(2L); sa2.setSegmentScriptId(7L); sa2.setStatus("READY");
            sa2.setAudioToken("tok-h2"); sa2.setAudioDurationMs(20000);
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L, 7L))).thenReturn(List.of(sa1, sa2));

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);

            assertEquals(2, pages.get(0).getSegments().size());
            assertEquals("AUDIO_READY", pages.get(0).getNarrationStatus());
            verify(htmlSegmentAudioMapper).listByScriptIds(anyList());
            verify(htmlSegmentAudioMapper, never()).listByScript(anyLong());
        }

        @Test
        @DisplayName("Q-3: segment_marker 含 </script> 被 escapeJson 转义（防 bridge 提前闭合）")
        void getPages_EscapeJsonBlocksScriptClosure() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<html><body><h1 id=\"seg-1\">标题</h1></body></html>");
            unit.setIsTrusted(Boolean.TRUE);
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1);
            // 恶意 marker：提前闭合 <script> 再注入 img onerror
            seg.setSegmentMarker("</script><img src=x onerror=alert(1)>");
            seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();

            // 只检查 bridge JSON 部分（var segs=[...];）—— 不含 bridge 自身的 </script> 关闭标签
            int jsonStart = html.indexOf("var segs=") + "var segs=".length();
            int jsonEnd = html.indexOf(";function", jsonStart);
            String segJson = html.substring(jsonStart, jsonEnd);
            assertFalse(segJson.contains("</script>"),
                    "bridge JSON 中出现裸 </script> 可提前闭合注入（Q-3 未生效）");
            assertTrue(segJson.contains("\\u003c/script\\u003e"),
                    "期望 </script> 被转义为反斜杠u003c...反斜杠u003e");
        }

        @Test
        @DisplayName("Q-4: is_trusted=false 注入 CSP nonce + bridge script nonce（严格模式防御）")
        void getPages_CspNonceForUntrusted() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<html><head></head><body><h1 id=\"seg-1\">标题</h1></body></html>");
            unit.setIsTrusted(Boolean.FALSE);   // 未标记可信 → 严格模式
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1); seg.setSegmentMarker("seg-1"); seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();

            assertTrue(html.contains("Content-Security-Policy"), "严格模式应注入 CSP meta");
            assertTrue(html.contains("script-src 'nonce-"), "CSP 应使用 nonce 机制");
            assertTrue(html.matches("(?s).*<script nonce=\"[0-9a-f]{32}\">.*"),
                    "bridge script 应带 nonce=\"{32 hex}\"");
        }

        @Test
        @DisplayName("Q-4: is_trusted=true 不注入 CSP（保留教师课件自有 script 功能）")
        void getPages_NoCspForTrusted() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<html><body><h1 id=\"seg-1\">标题</h1><script>alert('课件脚本')</script></body></html>");
            unit.setIsTrusted(Boolean.TRUE);   // 可信教师课件 → 宽松保留 script
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1); seg.setSegmentMarker("seg-1"); seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();

            assertFalse(html.contains("Content-Security-Policy"),
                    "is_trusted=true 不应注入 CSP（会阻止教师课件自有脚本）");
            // 教师自有脚本保留（宽松 sanitize 未破坏）
            assertTrue(html.contains("alert('课件脚本')"));
        }

        @Test
        @DisplayName("U-5: 多 READY 音色确定性 —— 取 SQL 排序后首个（最新完成/默认音色）")
        void getPages_PptDeterministicReadyAudio() {
            SlidePptPage page = new SlidePptPage();
            page.setId(10L); page.setCourseId(1L); page.setSectionId(5L);
            page.setPageNumber(1); page.setSlideId(2L);
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of(page));
            SlidePptPageScript script = new SlidePptPageScript();
            script.setId(3L); script.setPptPageId(10L); script.setScriptText("讲述稿");
            when(pptScriptMapper.listActiveByPageIds(List.of(10L))).thenReturn(List.of(script));
            // SQL 已按 is_default DESC, completed_at DESC 排序：旧的在前会被 ORDER BY 排后；
            // Java 侧仅取 findFirst → 结果必须是列表第一个（确定性，非随机）
            SlidePptPageAudio oldVoice = new SlidePptPageAudio();
            oldVoice.setId(41L); oldVoice.setScriptId(3L); oldVoice.setStatus("READY");
            oldVoice.setAudioToken("tok-old"); oldVoice.setAudioDurationMs(10000);
            SlidePptPageAudio newVoice = new SlidePptPageAudio();
            newVoice.setId(42L); newVoice.setScriptId(3L); newVoice.setStatus("READY");
            newVoice.setAudioToken("tok-new"); newVoice.setAudioDurationMs(20000);
            // 模拟 Mapper 已按 is_default DESC, completed_at DESC 排序返回：最新(42) 在前
            when(pptAudioMapper.listByScriptIds(List.of(3L))).thenReturn(List.of(newVoice, oldVoice));
            when(pptFlowMapper.listBySection(5L)).thenReturn(List.of());

            List<SlidePageVO> pages = slideService.getPages(1L, 5L, null);

            assertEquals("tok-new", pages.get(0).getAudio().getToken(),
                    "应确定性选择列表首个 READY（=最新完成/默认音色），而非随机 findFirst");
        }

        @Test
        @DisplayName("P0-B (I2): 学生端无参数入口（courseId 无 section/chapter 参数）返回 v2 课件首个 section 组，非 0 页空态")
        void getPages_NoParamFallsBackToFirstSectionV2() {
            // v2 PPT 页跨 2 个 section：修复前 getPages(courseId, null, null) 只走 legacy → 恒空
            SlidePptPage p1 = new SlidePptPage();
            p1.setId(10L); p1.setCourseId(1L); p1.setSectionId(5L);
            p1.setChapterId(7L); p1.setPageNumber(1); p1.setSlideId(2L);
            SlidePptPage p2 = new SlidePptPage();
            p2.setId(11L); p2.setCourseId(1L); p2.setSectionId(5L);
            p2.setChapterId(7L); p2.setPageNumber(2); p2.setSlideId(2L);
            SlidePptPage p3 = new SlidePptPage();
            p3.setId(12L); p3.setCourseId(1L); p3.setSectionId(6L);
            p3.setChapterId(8L); p3.setPageNumber(1); p3.setSlideId(3L);
            when(pptPageMapper.listByCourse(1L)).thenReturn(List.of(p1, p2, p3));
            SlidePptPageScript s1 = new SlidePptPageScript();
            s1.setId(3L); s1.setPptPageId(10L); s1.setScriptText("脚本1");
            SlidePptPageScript s2 = new SlidePptPageScript();
            s2.setId(4L); s2.setPptPageId(11L); s2.setScriptText("脚本2");
            when(pptScriptMapper.listActiveByPageIds(List.of(10L, 11L))).thenReturn(List.of(s1, s2));
            SlidePptPageAudio a1 = new SlidePptPageAudio();
            a1.setId(1L); a1.setScriptId(3L); a1.setStatus("READY");
            a1.setAudioToken("tok1"); a1.setAudioDurationMs(10000);
            SlidePptPageAudio a2 = new SlidePptPageAudio();
            a2.setId(2L); a2.setScriptId(4L); a2.setStatus("READY");
            a2.setAudioToken("tok2"); a2.setAudioDurationMs(20000);
            when(pptAudioMapper.listByScriptIds(List.of(3L, 4L))).thenReturn(List.of(a1, a2));
            when(pptFlowMapper.listBySection(5L)).thenReturn(List.of());

            // 无参数入口：sectionId / chapterId 均为 null
            List<SlidePageVO> pages = slideService.getPages(1L, null, null);

            // 修复核心断言：不再 0 页空态；取最小 sectionId(5) 的完整组（2 页）
            assertEquals(2, pages.size(), "无参数入口必须返回 v2 课件页，不得 0 页空态");
            assertEquals(5L, pages.get(0).getSectionId());
            assertEquals(1, pages.get(0).getPageNumber());
            assertEquals(2, pages.get(1).getPageNumber());
            assertEquals("PPT_RENDERED", pages.get(0).getContentType());
        }

        @Test
        @DisplayName("P0-B (I2): 无参数入口 course 级兜底 —— v2 HTML unit 存在时返回该单元")
        void getPages_NoParamHtmlUnitFallback() {
            when(pptPageMapper.listByCourse(1L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setHtmlSanitized("<h1>t</h1>"); unit.setDetectedSegments(1);
            when(htmlUnitMapper.listByCourse(1L)).thenReturn(List.of(unit));
            SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
            seg.setId(6L); seg.setSegmentIndex(1); seg.setScriptText("段1");
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(seg));
            SlideHtmlSegmentAudio segAudio = new SlideHtmlSegmentAudio();
            segAudio.setId(7L); segAudio.setSegmentScriptId(6L); segAudio.setStatus("READY");
            segAudio.setAudioToken("tok-html"); segAudio.setAudioDurationMs(15000);
            when(htmlSegmentAudioMapper.listByScriptIds(List.of(6L))).thenReturn(List.of(segAudio));

            List<SlidePageVO> pages = slideService.getPages(1L, null, null);

            assertEquals(1, pages.size(), "无参数入口必须返回 v2 HTML 单元，不得 0 页空态");
            assertEquals("HTML_DIRECT", pages.get(0).getContentType());
            assertNotNull(pages.get(0).getSegments());
        }

        @Test
        @DisplayName("P0-F (I2): 章节级入口挂载 flows（从页面推断 sectionId），学生端 BRANCH/SKIP 不再退化线性")
        void getPages_ChapterLevelLoadsFlows() {
            // 章节级课件页（section_id 有值 —— V310 回填后挂在该章节第一个 section 下）
            SlidePptPage page = new SlidePptPage();
            page.setId(10L); page.setCourseId(1L); page.setSectionId(5L);
            page.setChapterId(7L); page.setPageNumber(1); page.setSlideId(2L);
            when(pptPageMapper.listByChapter(7L)).thenReturn(List.of(page));
            SlidePptPageScript script = new SlidePptPageScript();
            script.setId(3L); script.setPptPageId(10L); script.setScriptText("讲述稿");
            when(pptScriptMapper.listActiveByPageIds(List.of(10L))).thenReturn(List.of(script));
            SlidePptPageAudio audio = new SlidePptPageAudio();
            audio.setId(4L); audio.setScriptId(3L); audio.setStatus("READY");
            audio.setAudioToken("tok123"); audio.setAudioDurationMs(30000);
            when(pptAudioMapper.listByScriptIds(List.of(3L))).thenReturn(List.of(audio));
            // 修复核心：章节级入口必须能按推断 sectionId 查到 flows（此前 listBySection(null) 恒空）
            SlidePptFlow flow = new SlidePptFlow();
            flow.setFromPageId(10L); flow.setToPageId(11L); flow.setFlowType("BRANCH_DEPENDS");
            when(pptFlowMapper.listBySection(5L)).thenReturn(List.of(flow));

            List<SlidePageVO> pages = slideService.getPages(1L, null, 7L);

            assertEquals(1, pages.size());
            assertEquals("BRANCH_DEPENDS", pages.get(0).getFlows().get(0).getFlowType(),
                    "章节级入口必须挂载 flows，学生端 BRANCH/SKIP 规则须真实生效");
        }

        @Test
        @DisplayName("P0-1: 3 段 h2+p 课件 —— 锚点落在各自标题，段内段落同步标注（修复双增错位）")
        void getPages_V2HtmlThreeSegmentsAnchoredCorrectly() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setIsTrusted(Boolean.TRUE);
            unit.setHtmlSanitized("<html><body>"
                    + "<h2>第一章</h2><p>内容A</p>"
                    + "<h2>第二章</h2><p>内容B</p>"
                    + "<h2>第三章</h2><p>内容C</p>"
                    + "</body></html>");
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(
                    htmlSeg(1, "seg-1"), htmlSeg(2, "seg-2"), htmlSeg(3, "seg-3")));
            when(htmlSegmentAudioMapper.listByScriptIds(anyList())).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();
            Document doc = Jsoup.parse(html);
            Elements h2s = doc.select("h2");
            Elements ps = doc.select("p");
            // 3 段锚点必须在各自标题上（L0：点哪段播哪段）
            assertEquals(3, h2s.size());
            assertEquals("1", h2s.get(0).attr("data-segment"), "第 1 段锚点应在第一章标题");
            assertEquals("2", h2s.get(1).attr("data-segment"), "第 2 段锚点应在第二章标题");
            assertEquals("3", h2s.get(2).attr("data-segment"), "第 3 段锚点应在第三章标题");
            // 段内段落同步标注（段高亮覆盖整段），且无错位
            assertEquals(3, ps.size());
            assertEquals("1", ps.get(0).attr("data-segment"), "内容A 应归入第 1 段");
            assertEquals("2", ps.get(1).attr("data-segment"), "内容B 应归入第 2 段");
            assertEquals("3", ps.get(2).attr("data-segment"), "内容C 应归入第 3 段");
            // 无双增：不得出现不存在的第 4 段
            assertTrue(doc.select("[data-segment='4']").isEmpty(),
                    "双增 bug 会导致出现 data-segment='4'（净增 2）");
        }

        @Test
        @DisplayName("P0-1: 5 段嵌套 div 课件 —— 全部锚点正确（容器递归标注）")
        void getPages_V2HtmlFiveNestedDivsAnchoredCorrectly() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setIsTrusted(Boolean.TRUE);
            unit.setHtmlSanitized("<html><body>"
                    + "<div><div><h2>第一章</h2><p>内容A</p></div></div>"
                    + "<div><h2>第二章</h2><p>内容B</p></div>"
                    + "<div><div><div><h2>第三章</h2><p>内容C</p></div></div></div>"
                    + "<div><h2>第四章</h2><p>内容D</p></div>"
                    + "<h2>第五章</h2><p>内容E</p>"
                    + "</body></html>");
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(
                    htmlSeg(1, "seg-1"), htmlSeg(2, "seg-2"), htmlSeg(3, "seg-3"),
                    htmlSeg(4, "seg-4"), htmlSeg(5, "seg-5")));
            when(htmlSegmentAudioMapper.listByScriptIds(anyList())).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();
            Document doc = Jsoup.parse(html);
            Elements h2s = doc.select("h2");
            Elements ps = doc.select("p");
            assertEquals(5, h2s.size());
            for (int i = 0; i < 5; i++) {
                assertEquals(String.valueOf(i + 1), h2s.get(i).attr("data-segment"),
                        "第 " + (i + 1) + " 段锚点必须在第 " + (i + 1) + " 个标题上");
                assertEquals(String.valueOf(i + 1), ps.get(i).attr("data-segment"),
                        "第 " + (i + 1) + " 段内容必须归入第 " + (i + 1) + " 段");
            }
        }

        @Test
        @DisplayName("P0-1: 0 段空 HTML —— 无 data-segment 注入（不误标空文档）")
        void getPages_V2HtmlEmptyNoDataSegment() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setIsTrusted(Boolean.TRUE);
            unit.setHtmlSanitized("<html><body></body></html>");
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(htmlSeg(1, "seg-1")));
            when(htmlSegmentAudioMapper.listByScriptIds(anyList())).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();
            Document doc = Jsoup.parse(html);
            assertTrue(doc.select("[data-segment]").isEmpty(),
                    "空 HTML 不应注入任何 data-segment 锚点");
        }

        @Test
        @DisplayName("P0-1: 检测失败（无可分段内容）—— fallback 无 data-segment（不误标 script/不可见内容）")
        void getPages_V2HtmlDetectionFailureNoDataSegment() {
            when(pptPageMapper.listBySection(5L)).thenReturn(List.of());
            SlideHtmlUnit unit = new SlideHtmlUnit();
            unit.setId(20L); unit.setCourseId(1L); unit.setSectionId(5L); unit.setSlideId(2L);
            unit.setIsTrusted(Boolean.TRUE);
            // 仅有 script（不可见内容，不参与分段）→ 检测无段 → 不应误标 data-segment
            unit.setHtmlSanitized("<html><body><script>var x=1;</script></body></html>");
            when(htmlUnitMapper.findBySection(5L)).thenReturn(unit);
            when(htmlSegmentScriptMapper.listActiveByUnit(20L)).thenReturn(List.of(htmlSeg(1, "seg-1")));
            when(htmlSegmentAudioMapper.listByScriptIds(anyList())).thenReturn(List.of());

            String html = slideService.getPages(1L, 5L, null).get(0).getHtmlContent();
            Document doc = Jsoup.parse(html);
            assertTrue(doc.select("[data-segment]").isEmpty(),
                    "无可分段内容时不应注入 data-segment（fallback）");
        }
    }

    @Nested
    @DisplayName("获取单页详情")
    class GetPage {
        @Test
        @DisplayName("存在时返回 SlidePageVO")
        void getPage_Found() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                SlidePage page = new SlidePage();
                page.setId(1L); page.setCourseId(1L); page.setPageNumber(1);
                page.setNarrationScript("测试讲述稿"); page.setNarrationStatus("AI_GENERATED");
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(page));

                SlidePageVO vo = slideService.getPage(1L, 1);
                assertNotNull(vo);
                assertEquals("测试讲述稿", vo.getNarrationScript());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("不存在时抛 SLIDE_PAGE_NOT_FOUND")
        void getPage_NotFound() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.getPage(1L, 999));
                assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        private void setupAdminContext() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }

    @Nested
    @DisplayName("更新页面")
    class UpdatePage {
        @Test
        @DisplayName("课件不存在抛 COURSE_NOT_FOUND")
        void updatePage_CourseNotFound() {
            SlidePage existingPage = new SlidePage();
            existingPage.setId(1L); existingPage.setCourseId(1L); existingPage.setPageNumber(1);
            when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
            when(courseRepository.selectById(1L)).thenReturn(null);
            BusinessException e = assertThrows(BusinessException.class,
                    () -> slideService.updatePage(1L, 1, Map.of("narrationScript", "test")));
            assertEquals(ErrorCode.COURSE_NOT_FOUND.getCode(), e.getCode());
        }

        @Test
        @DisplayName("页面不存在抛异常")
        void updatePage_NotFound() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                Course c = new Course();
                c.setId(888L);
                c.setTeacherId(888L);
                when(courseRepository.selectById(888L)).thenReturn(c);
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.updatePage(888L, 999, Map.of("narrationScript", "test")));
                assertEquals(ErrorCode.SLIDE_PAGE_NOT_FOUND.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Nested
    @DisplayName("排序页面")
    class Reorder {
        @Test
        @DisplayName("重新排序调用 updateById")
        void reorderPages_CallsUpdate() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                SlidePage page = new SlidePage();
                page.setId(1L); page.setCourseId(1L); page.setPageNumber(1);
                when(slidePageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(page));

                slideService.reorderPages(1L, List.of(Map.of("pageNumber", 1, "newPageNumber", 2)));
                verify(slidePageMapper, times(2)).updateById(any());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("排序忽略不变项")
        void reorderPages_SkipsUnchanged() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                when(courseRepository.selectById(1L)).thenReturn(new Course());
                slideService.reorderPages(1L, List.of(Map.of("pageNumber", 1, "newPageNumber", 1)));
                verify(slidePageMapper, never()).updateById(any());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Nested
    @DisplayName("HTML 上传")
    class HtmlUpload {

        private void setupAdminContext() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    1L, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @Test
        @DisplayName("HTML 文件上传成功")
        void uploadHtmlFile_Success() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                when(courseSlideMapper.insert(any(CourseSlide.class))).thenAnswer(inv -> {
                    CourseSlide s = inv.getArgument(0);
                    s.setId(43L);
                    return 1;
                });
                when(slidePageMapper.insert(any(SlidePage.class))).thenReturn(1);

                MockMultipartFile file = new MockMultipartFile(
                        "file", "lesson.html", "text/html", "<p>File Upload Test</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, file, null, null);

                assertNotNull(resp);
                assertEquals(1, resp.getTotalPages());
                assertEquals(2, resp.getStatus());
                assertEquals(43L, resp.getSlideId().longValue());
                verify(courseSlideMapper).insert(any(CourseSlide.class));
                verify(slidePageMapper).insert(any(SlidePage.class));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("HTML 文件超过 5MB 抛异常")
        void uploadHtmlFile_TooLarge() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                MockMultipartFile bigFile = new MockMultipartFile(
                        "file", "big.html", "text/html", new byte[5 * 1024 * 1024 + 1]);

                BusinessException e = assertThrows(BusinessException.class,
                        () -> slideService.uploadHtmlFile(1L, bigFile, null, null));
                assertEquals(ErrorCode.HTML_TOO_LARGE.getCode(), e.getCode());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("HTML UPSERT — 同一 chapterId 重复上传,复用 slide_id 且保留 audio 元数据")
        void uploadHtmlFile_Upsert() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                CourseChapter chapter = new CourseChapter();
                chapter.setId(10L);
                chapter.setCourseId(1L);
                when(courseChapterRepository.selectById(10L)).thenReturn(chapter);

                CourseSection section = new CourseSection();
                section.setId(10L);
                section.setCourseId(1L);
                section.setVersion(1);
                when(courseSectionRepository.selectById(10L)).thenReturn(section);
                when(courseSectionRepository.updateById(any(CourseSection.class))).thenReturn(1);

                CourseSlide existing = new CourseSlide();
                existing.setId(43L);
                existing.setCourseId(1L);
                existing.setChapterId(10L);
                existing.setFileName("old.html");
                existing.setFileUrl("html:inline");
                existing.setStatus(2);
                when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
                when(courseSlideMapper.updateById(any(CourseSlide.class))).thenReturn(1);

                // 模拟 page=1 已存在(且含 audio 元数据)
                SlidePage existingPage = new SlidePage();
                existingPage.setId(100L);
                existingPage.setSlideId(43L);
                existingPage.setCourseId(1L);
                existingPage.setSectionId(10L);
                existingPage.setPageNumber(1);
                existingPage.setContentType("HTML_DIRECT");
                existingPage.setHtmlContent("<p>Old HTML</p>");
                existingPage.setNarrationAudioUrl("/api/courses/1/slides/pages/1/audio?sectionId=10&v=2&token=abc123");
                existingPage.setAudioDuration(120);
                existingPage.setSegmentCount(15);
                existingPage.setNarrationStatus("AUDIO_READY");
                // 注意: 第一次 selectOne 找 CourseSlide, 第二次找 SlidePage
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
                when(slidePageMapper.updateById(any(SlidePage.class))).thenReturn(1);

                MockMultipartFile newFile = new MockMultipartFile(
                        "file", "new.html", "text/html", "<p>New content</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, newFile, 10L, 10L);

                assertEquals(43L, resp.getSlideId().longValue());
                // P1-C 修复验证: 不应 delete 旧 page,不应 insert 新 page
                verify(slidePageMapper, never()).delete(any(LambdaQueryWrapper.class));
                verify(slidePageMapper, never()).insert(any(SlidePage.class));
                verify(courseSlideMapper, never()).insert(any(CourseSlide.class));
                verify(courseSlideMapper).updateById(any(CourseSlide.class));
                verify(slidePageMapper).updateById(any(SlidePage.class));
                // audio 元数据应保留: 在 updateById 的参数上验证
                org.mockito.ArgumentCaptor<SlidePage> pageCaptor =
                        org.mockito.ArgumentCaptor.forClass(SlidePage.class);
                verify(slidePageMapper).updateById(pageCaptor.capture());
                SlidePage updated = pageCaptor.getValue();
                assertEquals("<p>New content</p>", updated.getHtmlContent());
                assertEquals("/api/courses/1/slides/pages/1/audio?sectionId=10&v=2&token=abc123",
                        updated.getNarrationAudioUrl());
                assertEquals(15, updated.getSegmentCount().intValue());
                assertEquals("AUDIO_READY", updated.getNarrationStatus());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("仅含 <script> 的 HTML 上传成功（课件模式允许 script，XSS 由 iframe sandbox 兜底）")
        void uploadHtmlFile_ScriptContentPreserved() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);
                when(courseSlideMapper.insert(any(CourseSlide.class))).thenAnswer(inv -> {
                    CourseSlide s = inv.getArgument(0);
                    s.setId(44L);
                    return 1;
                });
                when(slidePageMapper.insert(any(SlidePage.class))).thenReturn(1);

                MockMultipartFile xssFile = new MockMultipartFile(
                        "file", "xss.html", "text/html", "<script>alert(1)</script>".getBytes());

                // HtmlSanitizer.sanitizeForCourseware() 是宽松模式，
                // 允许 script 标签（安全由前端 iframe sandbox 兜底），
                // 因此上传应成功，不抛异常
                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, xssFile, null, null);
                assertNotNull(resp);
                assertEquals(44L, resp.getSlideId().longValue());
                verify(courseSlideMapper).insert(any(CourseSlide.class));
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("P0-3 — v2 unit 存在时替换 HTML 同步更新 unit（html_sanitized + is_trusted + 自动段检测），修复替换静默失效")
        void uploadHtmlFileV2UnitUpdateTest() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                CourseSection section = new CourseSection();
                section.setId(7L);
                section.setCourseId(1L);
                section.setVersion(1);
                when(courseSectionRepository.selectById(7L)).thenReturn(section);
                when(courseSectionRepository.updateById(any(CourseSection.class))).thenReturn(1);

                // v1 course_slides 已存在（UPSERT 路径）
                CourseSlide existing = new CourseSlide();
                existing.setId(43L);
                existing.setCourseId(1L);
                existing.setSectionId(7L);
                existing.setFileName("old.html");
                existing.setFileUrl("html:inline");
                existing.setStatus(2);
                when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
                when(courseSlideMapper.updateById(any(CourseSlide.class))).thenReturn(1);

                // v1 slide_pages 已存在（UPSERT in-place）
                SlidePage existingPage = new SlidePage();
                existingPage.setId(100L);
                existingPage.setSlideId(43L);
                existingPage.setCourseId(1L);
                existingPage.setSectionId(7L);
                existingPage.setPageNumber(1);
                existingPage.setContentType("HTML_DIRECT");
                existingPage.setHtmlContent("<p>Old</p>");
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
                when(slidePageMapper.updateById(any(SlidePage.class))).thenReturn(1);

                // v2 unit 已存在 → 替换必须同步更新它（P0-3 根因修复）
                SlideHtmlUnit v2Unit = new SlideHtmlUnit();
                v2Unit.setId(500L);
                v2Unit.setCourseId(1L);
                v2Unit.setSectionId(7L);
                v2Unit.setSlideId(43L);
                v2Unit.setHtmlContent("<p>Old v2</p>");
                v2Unit.setHtmlSanitized("<p>Old v2</p>");
                v2Unit.setIsTrusted(true);
                v2Unit.setVersion(1);
                when(htmlUnitMapper.findBySection(7L)).thenReturn(v2Unit);
                when(htmlUnitMapper.updateById(any(SlideHtmlUnit.class))).thenReturn(1);
                // 自动段检测：新内容含 2 个标题 → 2 段
                when(htmlSegmentDetector.detectSegments(anyString())).thenReturn(List.of(
                        new com.microcourse.plugin.interactive.dto.SegmentInfo(1, "seg-1", "#seg-1", "一"),
                        new com.microcourse.plugin.interactive.dto.SegmentInfo(2, "seg-2", "#seg-2", "二")));

                MockMultipartFile newFile = new MockMultipartFile(
                        "file", "new.html", "text/html",
                        "<h1>新标题一</h1><p>内容一</p><h2>新标题二</h2><p>内容二</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, newFile, null, 7L);

                assertEquals(43L, resp.getSlideId().longValue());
                // P0-3 核心验证：v2 unit 必须被 update（且不可 delete / insert）
                verify(htmlUnitMapper).findBySection(7L);
                verify(htmlUnitMapper, never()).deleteById(anyLong());
                org.mockito.ArgumentCaptor<SlideHtmlUnit> unitCaptor =
                        org.mockito.ArgumentCaptor.forClass(SlideHtmlUnit.class);
                verify(htmlUnitMapper).updateById(unitCaptor.capture());
                SlideHtmlUnit updatedUnit = unitCaptor.getValue();
                // 新内容同步进 v2 unit（学生端 getPages v2 优先 → 读到新内容）
                assertTrue(updatedUnit.getHtmlSanitized().contains("新标题一"));
                assertTrue(updatedUnit.getHtmlSanitized().contains("新标题二"));
                assertEquals(Boolean.TRUE, updatedUnit.getIsTrusted());
                // 自动段检测双保险：detected_segments 一次写入
                assertEquals(2, updatedUnit.getDetectedSegments().intValue());
                verify(htmlSegmentDetector).detectSegments(anyString());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }

        @Test
        @DisplayName("P0-3 — v2 unit 不存在时保持 v1 流程（不创建 v2 unit，不调用检测）")
        void uploadHtmlFile_NoV2UnitKeepsV1() {
            setupAdminContext();
            try {
                Course course = new Course();
                course.setId(1L);
                course.setTeacherId(1L);
                when(courseRepository.selectById(1L)).thenReturn(course);

                CourseSection section = new CourseSection();
                section.setId(7L);
                section.setCourseId(1L);
                section.setVersion(1);
                when(courseSectionRepository.selectById(7L)).thenReturn(section);
                when(courseSectionRepository.updateById(any(CourseSection.class))).thenReturn(1);

                CourseSlide existing = new CourseSlide();
                existing.setId(43L);
                existing.setCourseId(1L);
                existing.setSectionId(7L);
                existing.setStatus(2);
                when(courseSlideMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
                when(courseSlideMapper.updateById(any(CourseSlide.class))).thenReturn(1);

                SlidePage existingPage = new SlidePage();
                existingPage.setId(100L);
                existingPage.setSlideId(43L);
                existingPage.setCourseId(1L);
                existingPage.setSectionId(7L);
                existingPage.setPageNumber(1);
                existingPage.setContentType("HTML_DIRECT");
                when(slidePageMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingPage);
                when(slidePageMapper.updateById(any(SlidePage.class))).thenReturn(1);

                // v2 unit 不存在
                when(htmlUnitMapper.findBySection(7L)).thenReturn(null);

                MockMultipartFile file = new MockMultipartFile(
                        "file", "new.html", "text/html", "<p>New v1 only</p>".getBytes());

                SlideUploadResponse resp = slideService.uploadHtmlFile(1L, file, null, 7L);
                assertNotNull(resp);
                // v1 流程正常 + 不触碰 v2 unit / 不调用检测
                verify(courseSlideMapper).updateById(any(CourseSlide.class));
                verify(htmlUnitMapper).findBySection(7L);
                verify(htmlUnitMapper, never()).updateById(any(SlideHtmlUnit.class));
                verify(htmlSegmentDetector, never()).detectSegments(anyString());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    private SlideHtmlSegmentScript htmlSeg(int idx, String marker) {
        SlideHtmlSegmentScript seg = new SlideHtmlSegmentScript();
        seg.setId(100L + idx);
        seg.setSegmentIndex(idx);
        seg.setSegmentMarker(marker);
        seg.setScriptText("段" + idx);
        return seg;
    }
}

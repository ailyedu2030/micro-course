package com.microcourse.plugin.interactive;

import com.microcourse.entity.Course;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.dto.FlowEvaluateRequest;
import com.microcourse.plugin.interactive.dto.FlowEvaluateResponse;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.TtsOptionsVO;
import com.microcourse.plugin.interactive.entity.SectionQuiz;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.flow.FlowEngine;
import com.microcourse.plugin.interactive.mapper.SectionQuizMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.impl.CoursewareQueryServiceImpl;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
 *   <li>evaluateFlow: NEXT / BRANCH_DEPENDS / SKIP_IF_KNOWN 三态 + LINEAR 兜底 + 权限校验（P1-I-16）</li>
 *   <li>getTtsOptions: 返回 MiniMax 官方 models/voices（R-6，P1-I-16）</li>
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
    private SlidePageMapper slidePageMapper;
    private CourseRepository courseRepository;
    private EnrollmentRepository enrollmentRepository;
    private SectionQuizMapper sectionQuizMapper;
    private ExerciseRecordRepository exerciseRecordRepository;
    private com.microcourse.repository.LearningProgressRepository learningProgressRepository;
    private FlowEngine flowEngine;
    private CourseSectionRepository courseSectionRepository;
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
        slidePageMapper = mock(SlidePageMapper.class);
        courseRepository = mock(CourseRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        sectionQuizMapper = mock(SectionQuizMapper.class);
        exerciseRecordRepository = mock(ExerciseRecordRepository.class);
        learningProgressRepository = mock(com.microcourse.repository.LearningProgressRepository.class);
        flowEngine = mock(FlowEngine.class);
        courseSectionRepository = mock(CourseSectionRepository.class);
        service = new CoursewareQueryServiceImpl(pageMapper, pageScriptMapper,
                pageAudioMapper, flowMapper, unitMapper, segmentScriptMapper, segmentAudioMapper,
                slidePageMapper,
                mock(com.microcourse.plugin.interactive.cache.AudioStreamCache.class),
                flowEngine,
                courseSectionRepository,
                courseRepository, enrollmentRepository, sectionQuizMapper, exerciseRecordRepository,
                learningProgressRepository,
                mock(org.springframework.jdbc.core.JdbcTemplate.class));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getCoursewareTree: PPT course returns type=PPT with pages and audio status")
    void getPptTree() {
        // D-2: getCoursewareTree 现在做对象级 verifyAccess → 需要登录态 + 课程归属
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
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
        CoursewareTreeDTO tree = service.getCoursewareTree(42L, 99L, null);

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
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        when(pageMapper.listBySection(99L)).thenReturn(Collections.emptyList());
        when(unitMapper.findBySection(99L)).thenReturn(null);

        CoursewareTreeDTO tree = service.getCoursewareTree(42L, 99L, null);

        assertEquals("EMPTY", tree.getType());
        assertEquals(99L, tree.getSectionId());
        assertEquals(0, tree.getAudioReadyCount());
        assertEquals("PENDING", tree.getNarrationStatus());
    }

    @Test
    @DisplayName("getCoursewareTree: 章节级（chapterId）返回 PPT 树")
    void getChapterPptTree() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        SlidePptPage p1 = newPptPage(11L, 1, "Chapter Page 1", null, 42L);
        when(pageMapper.listByChapter(7L)).thenReturn(List.of(p1));
        when(unitMapper.findByChapter(7L)).thenReturn(null);
        when(flowMapper.listBySection(any())).thenReturn(Collections.emptyList());
        when(pageScriptMapper.findActiveByPage(11L)).thenReturn(null);

        CoursewareTreeDTO tree = service.getCoursewareTree(42L, null, 7L);

        assertEquals("PPT", tree.getType());
        assertEquals(1, tree.getPages().size());
        assertNull(tree.getSectionId(), "章节级树不绑定 sectionId");
    }

    @Test
    @DisplayName("getCoursewareTree: v1 HTML 已上传但单元未初始化 → 返回 HTML（待初始化）")
    void getPendingHtmlTree() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        when(pageMapper.listBySection(99L)).thenReturn(Collections.emptyList());
        when(unitMapper.findBySection(99L)).thenReturn(null);
        SlidePage html = new SlidePage();
        html.setId(1L);
        html.setCourseId(42L);
        html.setSectionId(99L);
        html.setContentType("HTML_DIRECT");
        when(slidePageMapper.selectOne(any())).thenReturn(html);

        CoursewareTreeDTO tree = service.getCoursewareTree(42L, 99L, null);

        assertEquals("HTML", tree.getType());
        assertNull(tree.getHtmlUnit(), "单元未初始化 → htmlUnit 为 null，前端编辑器预载后保存即创建");
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

    // ====== evaluateFlow (P1-I-16) ======

    /** 模拟当前用户：TEACHER 角色、ID=1，且为 course 42 的 owner（teacherId=1） */
    private void loginAsOwnerTeacher() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                1L, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
        SecurityContextHolder.setContext(ctx);
    }

    private void mockCourseOwnedBy1() {
        Course course = new Course();
        course.setId(42L);
        course.setTeacherId(1L);
        when(courseRepository.selectById(42L)).thenReturn(course);
    }

    private void mockSectionBelongsToCourse42() {
        CourseSection section = new CourseSection();
        section.setId(99L);
        section.setCourseId(42L);
        when(courseSectionRepository.selectById(99L)).thenReturn(section);
    }

    private PptFlowDTO newFlow(Long fromPage, Long toPage, String type) {
        PptFlowDTO f = new PptFlowDTO();
        f.setSectionId(99L);
        f.setFromPageId(fromPage);
        f.setToPageId(toPage);
        f.setFlowType(type);
        f.setPriority(0);
        return f;
    }

    @Test
    @DisplayName("evaluateFlow: NEXT 规则命中 → nextPageId + matchedType=NEXT")
    void evaluateFlowNext() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();
        when(flowEngine.decideNextPage(eq(99L), any())).thenReturn(2L);
        when(flowEngine.listFlows(99L)).thenReturn(List.of(
                newFlow(1L, 2L, "NEXT")));

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        FlowEvaluateResponse resp = service.evaluateFlow(42L, 99L, req);

        assertEquals(2L, resp.getNextPageId());
        assertEquals("NEXT", resp.getMatchedType());
    }

    @Test
    @DisplayName("evaluateFlow: BRANCH_DEPENDS quiz 通过（服务端读取 exercise_records）→ matchedType=BRANCH_DEPENDS")
    void evaluateFlowBranchQuizPassed() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();
        // quiz 5 属于 section 99
        SectionQuiz quiz = new SectionQuiz();
        quiz.setId(5L);
        quiz.setSectionId(99L);
        when(sectionQuizMapper.selectOne(any())).thenReturn(quiz);
        // 服务端从 exercise_records 读取通过状态（不信任客户端 lastQuizAnswer）
        ExerciseRecord rec = new ExerciseRecord();
        rec.setPassed(true);
        when(exerciseRecordRepository.selectList(any())).thenReturn(List.of(rec));
        when(flowEngine.decideNextPage(eq(99L), any())).thenReturn(3L);
        when(flowEngine.listFlows(99L)).thenReturn(List.of(
                newFlow(1L, 3L, "BRANCH_DEPENDS")));

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        req.setLastQuizId(5L);
        req.setLastQuizAnswer(true); // 客户端提交值——服务端忽略，改读 DB
        FlowEvaluateResponse resp = service.evaluateFlow(42L, 99L, req);

        assertEquals(3L, resp.getNextPageId());
        assertEquals("BRANCH_DEPENDS", resp.getMatchedType());
        verify(exerciseRecordRepository).selectList(any());
    }

    @Test
    @DisplayName("evaluateFlow: SKIP_IF_KNOWN 进度命中 → matchedType=SKIP_IF_KNOWN")
    void evaluateFlowSkip() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();
        when(flowEngine.decideNextPage(eq(99L), any())).thenReturn(4L);
        when(flowEngine.listFlows(99L)).thenReturn(List.of(
                newFlow(1L, 4L, "SKIP_IF_KNOWN")));

        // S-1（设计决策 3）: userProgress 服务端读取 —— 客户端提交值被忽略
        com.microcourse.entity.LearningProgress lp = new com.microcourse.entity.LearningProgress();
        lp.setVideoProgress(80);   // 0.8
        when(learningProgressRepository.findLatestByUserAndLesson(1L, 42L, 99L)).thenReturn(lp);

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        req.setUserProgress(0.05); // 客户端伪造低进度 —— 服务端忽略，仍读 DB 0.8
        FlowEvaluateResponse resp = service.evaluateFlow(42L, 99L, req);

        assertEquals(4L, resp.getNextPageId());
        assertEquals("SKIP_IF_KNOWN", resp.getMatchedType());
        verify(learningProgressRepository).findLatestByUserAndLesson(1L, 42L, 99L);
    }

    @Test
    @DisplayName("evaluateFlow: 无学习进度记录 → SKIP 不命中（服务端安全侧退化）")
    void evaluateFlowSkipNoProgress() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();
        // 服务端无 learning_progress → userProgress=null → SKIP 条件不成立 → 退化
        when(learningProgressRepository.findLatestByUserAndLesson(1L, 42L, 99L)).thenReturn(null);
        when(flowEngine.decideNextPage(eq(99L), any())).thenReturn(null);
        when(flowEngine.listFlows(99L)).thenReturn(List.of(
                newFlow(1L, 4L, "SKIP_IF_KNOWN")));

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        req.setUserProgress(0.99); // 客户端伪造高进度 —— 必须被忽略
        FlowEvaluateResponse resp = service.evaluateFlow(42L, 99L, req);

        assertNull(resp.getNextPageId());
        assertEquals("LINEAR", resp.getMatchedType());
    }

    @Test
    @DisplayName("evaluateFlow: 无规则匹配 → 退化为 LINEAR（nextPageId=null）")
    void evaluateFlowLinearFallback() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();
        when(flowEngine.decideNextPage(eq(99L), any())).thenReturn(null);
        when(flowEngine.listFlows(99L)).thenReturn(Collections.emptyList());

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        FlowEvaluateResponse resp = service.evaluateFlow(42L, 99L, req);

        assertNull(resp.getNextPageId());
        assertEquals("LINEAR", resp.getMatchedType());
    }

    @Test
    @DisplayName("evaluateFlow: 非 owner 教师 → NO_PERMISSION（IDOR）")
    void evaluateFlowDeniedForNonOwnerTeacher() {
        loginAsOwnerTeacher();
        // course 42 的 owner 是 99（非当前用户 1）
        Course course = new Course();
        course.setId(42L);
        course.setTeacherId(99L);
        when(courseRepository.selectById(42L)).thenReturn(course);

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.evaluateFlow(42L, 99L, req));

        assertEquals(10003, ex.getCode());
        // 无权访问 → 不应触碰 flow 引擎
        verify(flowEngine, never()).decideNextPage(any(), any());
    }

    @Test
    @DisplayName("evaluateFlow: section 不属于 course → RESOURCE_NOT_FOUND（IDOR）")
    void evaluateFlowSectionNotInCourse() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        CourseSection other = new CourseSection();
        other.setId(99L);
        other.setCourseId(999L); // 不属于 course 42
        when(courseSectionRepository.selectById(99L)).thenReturn(other);

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        req.setCurrentPageId(1L);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.evaluateFlow(42L, 99L, req));

        assertEquals(9006, ex.getCode());
    }

    @Test
    @DisplayName("evaluateFlow: currentPageId 缺失 → BAD_REQUEST_PARAM")
    void evaluateFlowMissingCurrentPage() {
        loginAsOwnerTeacher();
        mockCourseOwnedBy1();
        mockSectionBelongsToCourse42();

        FlowEvaluateRequest req = new FlowEvaluateRequest();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.evaluateFlow(42L, 99L, req));

        assertEquals(9005, ex.getCode());
    }

    // ====== getTtsOptions (P1-I-16) ======

    @Test
    @DisplayName("getTtsOptions: 返回 MiniMax 官方 models/voices + 默认值（R-6）")
    void getTtsOptionsReturnsMiniMaxOfficialEnum() {
        TtsOptionsVO vo = service.getTtsOptions();

        // MiniMax 官方模型枚举
        assertNotNull(vo.getModels());
        assertTrue(vo.getModels().contains("speech-2.8-hd"));
        assertTrue(vo.getModels().contains("speech-2.6-hd"));
        assertTrue(vo.getModels().contains("speech-01"));
        assertTrue(vo.getModels().contains("speech-02"));
        // 不允许残留非法枚举（R-6 修复目标）
        assertFalse(vo.getModels().contains("MiniMax-speech-01"));

        // MiniMax 官方 voice_id（含默认女声·甜美少女）
        assertNotNull(vo.getVoices());
        assertTrue(vo.getVoices().stream().anyMatch(v -> "female-shaonv".equals(v.getId())));
        assertTrue(vo.getVoices().stream().anyMatch(v -> "male-shaonian".equals(v.getId())));
        // 不允许残留 R-6 非法枚举
        assertFalse(vo.getVoices().stream().anyMatch(v -> "female-young".equals(v.getId())));

        // 默认值
        assertEquals("speech-2.8-hd", vo.getDefaultModel());
        assertEquals("female-shaonv", vo.getDefaultVoice());
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

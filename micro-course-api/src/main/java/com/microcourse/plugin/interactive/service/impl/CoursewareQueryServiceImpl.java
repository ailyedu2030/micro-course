package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.dto.FlowEvaluateRequest;
import com.microcourse.plugin.interactive.dto.FlowEvaluateResponse;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.dto.TtsOptionsVO;
import com.microcourse.plugin.interactive.entity.SectionQuiz;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SectionQuizMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import com.microcourse.plugin.interactive.flow.FlowEngine;
import com.microcourse.plugin.interactive.flow.FlowContext;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.LearningProgress;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 读侧统一实现.
 * <p>
 * 性能优化:
 * <ul>
 *   <li>用 in 批量查替代 N+1 (audio 按 script_id 分组)</li>
 *   <li>状态聚合走视图 v_slide_ppt_page_status / v_slide_html_unit_status (Phase 1 V308)</li>
 *   <li>Redis 缓存 mc:courseware:{sectionId}:meta TTL 10min (Phase 2 后续扩展)</li>
 * </ul>
 * 7-19 P1-C 兼容:
 * <ul>
 *   <li>resolveAudioToken 不依赖 pageNumber, 只用 audio_token (UK 校验)</li>
 *   <li>支持 PPT 和 HTML 两种课件的 audio_token 路由</li>
 * </ul>
 */
@Service
public class CoursewareQueryServiceImpl implements CoursewareQueryService {

    private static final Logger log = LoggerFactory.getLogger(CoursewareQueryServiceImpl.class);

    private final SlidePptPageMapper pageMapper;
    private final SlidePptPageScriptMapper pageScriptMapper;
    private final SlidePptPageAudioMapper pageAudioMapper;
    private final SlidePptFlowMapper flowMapper;
    private final SlideHtmlUnitMapper unitMapper;
    private final SlideHtmlSegmentScriptMapper segmentScriptMapper;
    private final SlideHtmlSegmentAudioMapper segmentAudioMapper;
    private final SlidePageMapper slidePageMapper;
    private final com.microcourse.plugin.interactive.cache.AudioStreamCache audioStreamCache;
    private final FlowEngine flowEngine;
    private final CourseSectionRepository courseSectionRepository;
    // P1-C-2 (IDOR + BRANCH 服务端读取)：课程归属 / 选课 / 测验完成状态
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SectionQuizMapper sectionQuizMapper;
    private final ExerciseRecordRepository exerciseRecordRepository;
    // S-1 (设计决策 3)：SKIP_IF_KNOWN userProgress 服务端读取（learning_progress）
    private final LearningProgressRepository learningProgressRepository;
    // D-1 (V328)：幽灵章节审计函数调用（audit_ghost_chapters()）
    private final JdbcTemplate jdbcTemplate;

    /** MiniMax 官方模型（application.yml 契约，R-6） */
    private static final List<String> TTS_MODELS = java.util.List.of(
            "speech-2.8-hd", "speech-2.6-hd", "speech-01", "speech-02");

    /** MiniMax 官方 voice_id（application.yml 注释预定义音色，R-6） */
    private static final List<TtsOptionsVO.VoiceOption> TTS_VOICES = java.util.List.of(
            new TtsOptionsVO.VoiceOption("female-shaonv", "女声·甜美少女"),
            new TtsOptionsVO.VoiceOption("female-qingxin", "女声·清新"),
            new TtsOptionsVO.VoiceOption("female-yujie", "女声·御姐"),
            new TtsOptionsVO.VoiceOption("female-warm", "女声·温暖"),
            new TtsOptionsVO.VoiceOption("male-shaonian", "男声·少年"),
            new TtsOptionsVO.VoiceOption("male-qingnian", "男声·青年"),
            new TtsOptionsVO.VoiceOption("male-dashu", "男声·大叔"),
            new TtsOptionsVO.VoiceOption("male-chengzhao", "男声·沉稳"));

    public CoursewareQueryServiceImpl(SlidePptPageMapper pageMapper,
                                       SlidePptPageScriptMapper pageScriptMapper,
                                       SlidePptPageAudioMapper pageAudioMapper,
                                       SlidePptFlowMapper flowMapper,
                                       SlideHtmlUnitMapper unitMapper,
                                       SlideHtmlSegmentScriptMapper segmentScriptMapper,
                                       SlideHtmlSegmentAudioMapper segmentAudioMapper,
                                       SlidePageMapper slidePageMapper,
                                       com.microcourse.plugin.interactive.cache.AudioStreamCache audioStreamCache,
                                       FlowEngine flowEngine,
                                       CourseSectionRepository courseSectionRepository,
                                       CourseRepository courseRepository,
                                       EnrollmentRepository enrollmentRepository,
                                       SectionQuizMapper sectionQuizMapper,
                                       ExerciseRecordRepository exerciseRecordRepository,
                                       LearningProgressRepository learningProgressRepository,
                                       JdbcTemplate jdbcTemplate) {
        this.pageMapper = pageMapper;
        this.pageScriptMapper = pageScriptMapper;
        this.pageAudioMapper = pageAudioMapper;
        this.flowMapper = flowMapper;
        this.unitMapper = unitMapper;
        this.segmentScriptMapper = segmentScriptMapper;
        this.segmentAudioMapper = segmentAudioMapper;
        this.slidePageMapper = slidePageMapper;
        this.audioStreamCache = audioStreamCache;
        this.flowEngine = flowEngine;
        this.courseSectionRepository = courseSectionRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sectionQuizMapper = sectionQuizMapper;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.learningProgressRepository = learningProgressRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CoursewareTreeDTO getCoursewareTree(Long courseId, Long sectionId, Long chapterId) {
        if (courseId == null || (sectionId == null && chapterId == null)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "courseId 与 sectionId/chapterId 必填（课时级或章节级二选一）");
        }

        // D-2 (IDOR 修复): 对象级 verifyAccess —— 此前仅校验 section→course 归属，
        // 未校验调用者对 course 的访问权，学生可访问未选课课件树（含 HTML 完整内容）。
        // 与 evaluateFlow 同构：ADMIN/ACADEMIC 通行；TEACHER 必须课程 owner；STUDENT 必须有选课记录。
        verifyCourseAccess(courseId);

        // 1. 一次性查 PPT pages (验证 + 数据复用, 避免 N+1)；课时级优先，章节级兜底
        List<SlidePptPage> pptPages = sectionId != null
                ? pageMapper.listBySection(sectionId)
                : pageMapper.listByChapter(chapterId);
        SlideHtmlUnit htmlUnit = sectionId != null
                ? unitMapper.findBySection(sectionId)
                : unitMapper.findByChapter(chapterId);

        // 【审计修复 BUG #4 + #8】 复用已查询的 pptPages 校验 section 归属,
        // 消除 BUG #8 的重复 SQL (N+1 → 1 query)
        validateSectionBelongsToCourse(courseId, sectionId, pptPages, htmlUnit);

        if (!pptPages.isEmpty()) {
            return buildPptTree(courseId, sectionId, pptPages);
        } else if (htmlUnit != null) {
            return buildHtmlTree(courseId, sectionId, htmlUnit);
        } else {
            // v1 HTML 页面已上传（HTML_DIRECT）但单元未初始化 → 仍按 HTML 类型返回，
            // 前端 HTML 模块编辑器预载内容，保存一次即创建单元（F-2026-08-07-13）
            SlidePage v1Html = slidePageMapper.selectOne(
                    new LambdaQueryWrapper<SlidePage>()
                            .eq(SlidePage::getCourseId, courseId)
                            .eq(sectionId != null, SlidePage::getSectionId, sectionId)
                            .eq(sectionId == null, SlidePage::getChapterId, chapterId)
                            .eq(SlidePage::getContentType, "HTML_DIRECT")
                            .orderByAsc(SlidePage::getId)
                            .last("LIMIT 1"));
            if (v1Html != null) {
                return pendingHtmlTree(courseId, sectionId);
            }
            return emptyTree(courseId, sectionId);
        }
    }

    private CoursewareTreeDTO pendingHtmlTree(Long courseId, Long sectionId) {
        CoursewareTreeDTO tree = new CoursewareTreeDTO();
        tree.setType("HTML");
        tree.setSectionId(sectionId);
        tree.setCourseId(courseId);
        tree.setHtmlUnit(null);
        tree.setNarrationStatus("PENDING");
        tree.setAudioReadyCount(0);
        return tree;
    }

    /**
     * 校验 sectionId 归属于 courseId (复用已查询的 pptPages/htmlUnit, 无额外 SQL).
     * <p>
     * 7-19 P0 防御: 即使 course_sections.id 是 PK, 仍需校验外键归属,
     * 防止 URL 篡改 (如 /api/courses/1/... 但实际访问 course=2 的资源).
     * </p>
     * <p>
     * 【BUG #8 修复】 接受已查询的 pptPages/htmlUnit 作参数, 避免重复 listBySection.
     * </p>
     */
    private void validateSectionBelongsToCourse(Long courseId, Long sectionId,
                                                List<SlidePptPage> pptPages,
                                                SlideHtmlUnit htmlUnit) {
        if (!pptPages.isEmpty()) {
            // 【BUG #21 修复】 校验所有 page 必须属于同一 course, 防数据污染
            for (SlidePptPage p : pptPages) {
                if (!courseId.equals(p.getCourseId())) {
                    log.warn("[CoursewareTree] courseId mismatch on page: path={} actual={}, pageId={}",
                            courseId, p.getCourseId(), p.getId());
                    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                            "section 内 page 跨 course 污染: courseId=" + courseId
                                    + " sectionId=" + sectionId + " pageId=" + p.getId());
                }
            }
        } else if (htmlUnit != null && !courseId.equals(htmlUnit.getCourseId())) {
            log.warn("[CoursewareTree] courseId mismatch (HTML): path={} actual={}, sectionId={}",
                    courseId, htmlUnit.getCourseId(), sectionId);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "section 不属于该 course: courseId=" + courseId + " sectionId=" + sectionId);
        }
        // 两者都为空 → emptyTree, 跳过校验 (无数据无法判断归属, 容忍)
    }

    private CoursewareTreeDTO buildPptTree(Long courseId, Long sectionId, List<SlidePptPage> pages) {
        CoursewareTreeDTO tree = new CoursewareTreeDTO();
        tree.setType("PPT");
        tree.setSectionId(sectionId);
        tree.setCourseId(pages.isEmpty() ? null : pages.get(0).getCourseId());
        tree.setLastUpdatedAt(LocalDateTime.now());

        // 2. 批量取脚本 (1 SQL per page)
        List<CoursewareTreeDTO.PptPageNode> nodes = new ArrayList<>(pages.size());
        int readyAudios = 0;
        LocalDateTime lastUpdate = null;
        for (SlidePptPage page : pages) {
            CoursewareTreeDTO.PptPageNode node = new CoursewareTreeDTO.PptPageNode();
            node.setPageId(page.getId());
            node.setPageNumber(page.getPageNumber());
            node.setPageTitle(page.getPageTitle());
            node.setImageUrl(page.getImageUrl());
            node.setThumbnailUrl(page.getThumbnailUrl());

            // active script
            SlidePptPageScript activeScript = pageScriptMapper.findActiveByPage(page.getId());
            if (activeScript != null) {
                node.setActiveScript(toPptScriptDTO(activeScript));
                List<SlidePptPageAudio> audios = pageAudioMapper.listByScript(activeScript.getId());
                List<PptAudioDTO> audioDTOs = audios.stream()
                        .map(this::toPptAudioDTO).collect(Collectors.toList());
                node.setAudios(audioDTOs);
                long ready = audios.stream()
                        .filter(a -> "READY".equals(a.getStatus())).count();
                node.setNarrationStatus(ready > 0 ? "AUDIO_READY" : "AUDIO_GENERATING");
                if (ready > 0) readyAudios += ready;
            } else {
                node.setNarrationStatus("PENDING");
            }
            nodes.add(node);
            if (page.getUpdatedAt() != null && (lastUpdate == null || page.getUpdatedAt().isAfter(lastUpdate))) {
                lastUpdate = page.getUpdatedAt();
            }
        }
        tree.setPages(nodes);

        // 3. flow（仅课时级课件支持页间跳转规则；章节级不适用）
        if (sectionId != null) {
            List<SlidePptFlow> flows = flowMapper.listBySection(sectionId);
            tree.setFlow(flows.stream().map(this::toPptFlowDTO).collect(Collectors.toList()));
        } else {
            tree.setFlow(Collections.emptyList());
        }

        tree.setAudioReadyCount(readyAudios);
        tree.setNarrationStatus(readyAudios > 0 ? "AUDIO_READY" : "AUDIO_GENERATING");
        if (lastUpdate != null) tree.setLastUpdatedAt(lastUpdate);
        return tree;
    }

    private CoursewareTreeDTO buildHtmlTree(Long courseId, Long sectionId, SlideHtmlUnit unit) {
        CoursewareTreeDTO tree = new CoursewareTreeDTO();
        tree.setType("HTML");
        tree.setSectionId(sectionId);
        tree.setCourseId(unit.getCourseId());
        tree.setLastUpdatedAt(unit.getUpdatedAt() != null ? unit.getUpdatedAt() : LocalDateTime.now());

        SlideHtmlUnitDTO unitDTO = toHtmlUnitDTO(unit);
        tree.setHtmlUnit(unitDTO);

        // segment scripts + audios
        List<SlideHtmlSegmentScript> activeSegments = segmentScriptMapper.listActiveByUnit(unit.getId());
        int readyAudios = 0;
        for (SlideHtmlSegmentScript seg : activeSegments) {
            List<SlideHtmlSegmentAudio> audios = segmentAudioMapper.listByScript(seg.getId());
            readyAudios += audios.stream()
                    .filter(a -> "READY".equals(a.getStatus())).count();
        }
        tree.setAudioReadyCount(readyAudios);
        tree.setNarrationStatus(readyAudios > 0 ? "AUDIO_READY" : "AUDIO_GENERATING");
        return tree;
    }

    private CoursewareTreeDTO emptyTree(Long courseId, Long sectionId) {
        CoursewareTreeDTO tree = new CoursewareTreeDTO();
        tree.setType("EMPTY");
        tree.setSectionId(sectionId);
        tree.setPages(Collections.emptyList());
        tree.setFlow(Collections.emptyList());
        tree.setNarrationStatus("PENDING");
        tree.setAudioReadyCount(0);
        return tree;
    }

    @Override
    public AudioStreamInfo resolveAudioToken(String token) {
        // 【BUG #29 修复 P1 性能】 先查 Redis 缓存 (TTL 5 min)
        Optional<AudioStreamInfo> cached = audioStreamCache.get(token);
        if (cached.isPresent()) {
            log.debug("[Audio-Stream] cache hit: token.length={}", token.length());
            return cached.get();
        }

        // 7-19 P1-C 兼容: 先查 PPT audio, 再查 HTML segment audio
        SlidePptPageAudio pptAudio = pageAudioMapper.findByToken(token);
        if (pptAudio != null) {
            AudioStreamInfo info = toStreamInfo(pptAudio, "PPT", pptAudio.getPptPageId());
            // 【BUG #23 修复】 查 page 获取真实 courseId, 用于 BUG #22 IDOR 校验
            SlidePptPage page = pageMapper.selectById(pptAudio.getPptPageId());
            info.setCourseId(page != null ? page.getCourseId() : null);
            // 【BUG #29 修复】 写回 Redis 缓存 (best-effort)
            audioStreamCache.put(token, info);
            return info;
        }
        SlideHtmlSegmentAudio htmlAudio = segmentAudioMapper.findByToken(token);
        if (htmlAudio != null) {
            AudioStreamInfo info = toStreamInfo(htmlAudio, "HTML", htmlAudio.getHtmlUnitId());
            // 【BUG #23 修复】 查 unit 获取真实 courseId, 用于 BUG #22 IDOR 校验
            SlideHtmlUnit unit = unitMapper.selectById(htmlAudio.getHtmlUnitId());
            info.setCourseId(unit != null ? unit.getCourseId() : null);
            // 【BUG #29 修复】 写回 Redis 缓存
            audioStreamCache.put(token, info);
            return info;
        }
        log.warn("[Audio-Stream] token not found (masked): token.length={}", token.length());
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "Audio token invalid: " + (token.length() > 8 ? token.substring(0, 8) + "..." : token));
    }

    @Override
    public TtsOptionsVO getTtsOptions() {
        TtsOptionsVO vo = new TtsOptionsVO();
        vo.setModels(TTS_MODELS);
        vo.setVoices(TTS_VOICES);
        vo.setDefaultModel("speech-2.8-hd");
        vo.setDefaultVoice("female-shaonv");
        return vo;
    }

    @Override
    public FlowEvaluateResponse evaluateFlow(Long courseId, Long sectionId, FlowEvaluateRequest request) {
        if (courseId == null || sectionId == null || request == null || request.getCurrentPageId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "courseId / sectionId / currentPageId 必填");
        }
        // P1-C-2 IDOR：校验调用者对 course 的访问权（ADMIN/ACADEMIC 通行；
        // TEACHER 必须课程 owner；STUDENT 必须有 APPROVED/COMPLETED 选课记录）
        verifyCourseAccess(courseId);
        // IDOR：校验 section 归属 course（复用 CoursewareTree 的防护语义）
        CourseSection section = courseSectionRepository.selectById(sectionId);
        if (section == null || !courseId.equals(section.getCourseId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "section 不属于该 course: courseId=" + courseId + " sectionId=" + sectionId);
        }
        // P1-C-2（设计决策 3）：BRANCH quiz 答案服务端读取，不信任客户端 lastQuizAnswer。
        // 客户端 lastQuizId 仅作 hint：必须属于本 section 的 quiz（section_quizzes），否则忽略；
        // 通过状态从 exercise_records 服务端读取（同 ExerciseRecordController.getAttemptSummary 数据源）。
        Long quizId = request.getLastQuizId();
        Boolean quizPassed = null;
        if (quizId != null) {
            SectionQuiz quiz = sectionQuizMapper.selectOne(
                    new LambdaQueryWrapper<SectionQuiz>()
                            .eq(SectionQuiz::getId, quizId)
                            .eq(SectionQuiz::getSectionId, sectionId));
            if (quiz == null) {
                log.warn("[FlowEvaluate] 忽略跨 section/伪造的 lastQuizId={} (sectionId={})", quizId, sectionId);
                quizId = null;
            } else {
                Long uid = SecurityUtil.getCurrentUserIdOpt();
                quizPassed = uid != null && exerciseRecordRepository.selectList(
                                new LambdaQueryWrapper<ExerciseRecord>()
                                        .eq(ExerciseRecord::getUserId, uid)
                                        .eq(ExerciseRecord::getExerciseId, quizId))
                        .stream().anyMatch(r -> Boolean.TRUE.equals(r.getPassed()));
                if (quizPassed == null) quizPassed = false;
            }
        }
        // S-1（设计决策 3 完整性）：SKIP_IF_KNOWN 的 userProgress 也必须服务端读取，
        // 不信任客户端 request.getUserProgress()（可伪造进度绕过教师配置的 SKIP 规则）。
        // 数据源：learning_progress（user_id + course_id + lesson_id 最新记录），
        // video_progress 0-100 → 0.0-1.0；无记录 → null（SKIP 不命中，退化为线性）。
        Long userId = SecurityUtil.getCurrentUserIdOpt();
        Double userProgress = null;
        if (userId != null && learningProgressRepository != null) {
            try {
                LearningProgress lp = learningProgressRepository.findLatestByUserAndLesson(
                        userId, courseId, sectionId);
                if (lp != null && lp.getVideoProgress() != null) {
                    userProgress = lp.getVideoProgress() / 100.0;
                    log.debug("[FlowEvaluate] SKIP userProgress 服务端读取: userId={}, section={}, progress={}",
                            userId, sectionId, userProgress);
                }
            } catch (Exception e) {
                // 服务端读取失败 → 不命中 SKIP（安全侧退化：宁可不跳，不伪造进度）
                log.warn("[FlowEvaluate] learning_progress 读取失败（SKIP 不命中）: {}", e.getMessage());
                userProgress = null;
            }
        }
        FlowContext context = new FlowContext(
                request.getCurrentPageId(),
                userId,
                userProgress,
                quizId,
                quizPassed);
        Long next = flowEngine.decideNextPage(sectionId, context);
        if (next == null) {
            return new FlowEvaluateResponse(null, "LINEAR");
        }
        // 命中的规则类型（供前端展示/日志）
        String matchedType = flowEngine.listFlows(sectionId).stream()
                .filter(f -> f.getFromPageId().equals(request.getCurrentPageId()))
                .filter(f -> f.getToPageId() != null && f.getToPageId().equals(next))
                .map(f -> f.getFlowType())
                .findFirst().orElse("NEXT");
        return new FlowEvaluateResponse(next, matchedType);
    }

    @Override
    public String auditGhostChapters() {
        // D-1 (V328)：幽灵章节审计是运维级只读操作，仅 ADMIN 可调用。
        // 审计本身不改数据；修复由人工 review 后执行 V329+ 后置 UPDATE。
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅 ADMIN 可执行幽灵章节审计");
        }
        try {
            String report = jdbcTemplate.queryForObject(
                    "SELECT audit_ghost_chapters()::text", String.class);
            if (report == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "幽灵章节审计返回空报告（V328 audit_ghost_chapters() 未生效？）");
            }
            log.info("[GhostChapter-Audit] 审计完成，报告长度={} 字符", report.length());
            return report;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GhostChapter-Audit] audit_ghost_chapters() 执行失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "幽灵章节审计执行失败: " + e.getMessage());
        }
    }

    /**
     * D-2 / P1-C-2 IDOR 防护：验证当前用户有权限访问此课程的课件（与 SlideController.verifyAccess 同构，
     * 双向防御；getCoursewareTree / evaluateFlow 统一走本方法）。
     * - ADMIN / ACADEMIC: 通行
     * - TEACHER: 必须是课程的所有者
     * - STUDENT: 必须已选此课（有 APPROVED/COMPLETED 的 enrollment 记录）
     */
    public void verifyCourseAccess(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (SecurityUtil.isAdmin() || SecurityUtil.isAcademic()) {
            return;
        }
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (SecurityUtil.hasRole("TEACHER")) {
            if (!currentUserId.equals(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程");
            }
            return;
        }
        if (SecurityUtil.hasRole("STUDENT")) {
            LambdaQueryWrapper<Enrollment> check = new LambdaQueryWrapper<>();
            check.eq(Enrollment::getUserId, currentUserId)
                    .eq(Enrollment::getCourseId, courseId)
                    .in(Enrollment::getEnrollmentStatus, "APPROVED", "COMPLETED")
                    .isNull(Enrollment::getDeletedAt);
            if (enrollmentRepository.selectCount(check) == 0) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "请先选课再查看课件");
            }
        }
    }

    // ====== Converters ======

    private PptScriptDTO toPptScriptDTO(SlidePptPageScript e) {
        PptScriptDTO d = new PptScriptDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private PptAudioDTO toPptAudioDTO(SlidePptPageAudio e) {
        PptAudioDTO d = new PptAudioDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private PptFlowDTO toPptFlowDTO(SlidePptFlow e) {
        PptFlowDTO d = new PptFlowDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private SlideHtmlUnitDTO toHtmlUnitDTO(SlideHtmlUnit e) {
        SlideHtmlUnitDTO d = new SlideHtmlUnitDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private AudioStreamInfo toStreamInfo(SlidePptPageAudio e, String type, Long ownerId) {
        AudioStreamInfo info = new AudioStreamInfo();
        info.setToken(e.getAudioToken());
        info.setAudioUrl(e.getAudioUrl());
        info.setCourseId(null);  // PPT audio 不冗余存 courseId, 通过 pptPageId 查
        info.setCoursewareType(type);
        info.setOwnerId(ownerId);
        info.setScriptId(e.getScriptId());
        info.setAudioDurationMs(e.getAudioDurationMs());
        info.setStatus(e.getStatus());
        info.setStoragePath(e.getStoragePath());
        info.setFileSizeBytes(e.getFileSizeBytes());
        return info;
    }

    private AudioStreamInfo toStreamInfo(SlideHtmlSegmentAudio e, String type, Long ownerId) {
        AudioStreamInfo info = new AudioStreamInfo();
        info.setToken(e.getAudioToken());
        info.setAudioUrl(e.getAudioUrl());
        info.setCourseId(null);  // HTML audio 不冗余存 courseId, 通过 htmlUnitId 查
        info.setCoursewareType(type);
        info.setOwnerId(ownerId);
        info.setScriptId(e.getSegmentScriptId());
        info.setSegmentIndex((long) e.getSegmentIndex());
        info.setAudioDurationMs(e.getAudioDurationMs());
        info.setStatus(e.getStatus());
        info.setStoragePath(e.getStoragePath());
        info.setFileSizeBytes(e.getFileSizeBytes());
        return info;
    }
}

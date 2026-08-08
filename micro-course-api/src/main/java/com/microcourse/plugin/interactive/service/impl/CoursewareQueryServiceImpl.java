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
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SectionQuiz;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
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
import java.util.Map;
import java.util.Objects;
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
    // G3-P1-C-1: 渲染状态透传（course_slides.status / error_message）
    private final CourseSlideMapper courseSlideMapper;
    // P14-C (N+1 修复): 教师课件树 Redis 缓存（PPT 分支，无动态 nonce 可安全缓存）
    private final com.microcourse.plugin.interactive.cache.CoursewarePagesCache pagesCache;

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
                                       JdbcTemplate jdbcTemplate,
                                       CourseSlideMapper courseSlideMapper,
                                       com.microcourse.plugin.interactive.cache.CoursewarePagesCache pagesCache) {
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
        this.courseSlideMapper = courseSlideMapper;
        this.pagesCache = pagesCache;
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

        // P14-C (N+1 修复): 教师课件树 Redis 缓存 —— PPT 分支无动态 nonce 可安全缓存。
        // 权限校验（verifyCourseAccess）独立于缓存 key 执行，缓存命中不跳过 IDOR 校验。
        // 缓存失效由所有写路径统一走 pagesCache.invalidateCourse 覆盖（上传/编辑/删除/flow/TTS 完成）。
        Optional<CoursewareTreeDTO> cachedTree = pagesCache.getTree(courseId, sectionId, chapterId);
        if (cachedTree != null && cachedTree.isPresent()) {
            return cachedTree.get();
        }

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

        CoursewareTreeDTO result;
        if (!pptPages.isEmpty()) {
            result = buildPptTree(courseId, sectionId, pptPages);
            // P14-C: 仅 PPT 树缓存（HTML 含动态 CSP nonce 不缓存，与 pages 严格模式同理）
            pagesCache.putTree(courseId, sectionId, chapterId, result);
            return result;
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

        // 2. 批量取 active scripts + audios（Q-2 同款模式: 2 SQL 取代 2N SQL）
        //    1) listActiveByPageIds → Map<pageId, activeScript>（1 SQL）
        //    2) listByScriptIds     → Map<scriptId, List<Audio>>（1 SQL, is_default DESC, completed_at DESC 排序）
        List<Long> pageIds = pages.stream().map(SlidePptPage::getId)
                .filter(Objects::nonNull).toList();
        List<SlidePptPageScript> activeScripts = pageIds.isEmpty()
                ? List.of() : pageScriptMapper.listActiveByPageIds(pageIds);
        Map<Long, SlidePptPageScript> scriptByPage = activeScripts.stream()
                .filter(s -> s.getPptPageId() != null)
                .collect(Collectors.toMap(SlidePptPageScript::getPptPageId, s -> s, (a, b) -> a));
        List<Long> scriptIds = activeScripts.stream().map(SlidePptPageScript::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, List<SlidePptPageAudio>> audiosByScript = scriptIds.isEmpty()
                ? Map.of()
                : pageAudioMapper.listByScriptIds(scriptIds).stream()
                        .filter(a -> a.getScriptId() != null)
                        .collect(Collectors.groupingBy(SlidePptPageAudio::getScriptId));

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

            // active script（内存 Map 查找, 无 SQL）
            SlidePptPageScript activeScript = scriptByPage.get(page.getId());
            if (activeScript != null) {
                node.setActiveScript(toPptScriptDTO(activeScript));
                List<SlidePptPageAudio> audios = audiosByScript.getOrDefault(
                        activeScript.getId(), Collections.emptyList());
                List<PptAudioDTO> audioDTOs = audios.stream()
                        .map(this::toPptAudioDTO).collect(Collectors.toList());
                node.setAudios(audioDTOs);
                long ready = audios.stream()
                        .filter(a -> "READY".equals(a.getStatus())).count();
                // P1-C(2026-08-09): FAILED 音频 → AUDIO_FAILED（诚实提示"生成失败"），
                // 而非一律 AUDIO_GENERATING；仅无 FAILED 且无 READY 时才为 AUDIO_GENERATING。
                if (ready > 0) {
                    node.setNarrationStatus("AUDIO_READY");
                    readyAudios += ready;
                } else if (audios.stream().anyMatch(a -> "FAILED".equals(a.getStatus()))) {
                    node.setNarrationStatus("AUDIO_FAILED");
                } else {
                    node.setNarrationStatus("AUDIO_GENERATING");
                }
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
        // P1-C(2026-08-09): 树级状态同样诚实 —— 全部页含 FAILED 且无 READY → AUDIO_FAILED，
        // 而非一律 AUDIO_GENERATING（防止教师/学生端误判"正在生成"）。
        boolean anyFailed = tree.getPages().stream()
                .anyMatch(n -> "AUDIO_FAILED".equals(n.getNarrationStatus()));
        tree.setNarrationStatus(readyAudios > 0 ? "AUDIO_READY"
                : (anyFailed ? "AUDIO_FAILED" : "AUDIO_GENERATING"));
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
        boolean anyFailed = false;
        for (SlideHtmlSegmentScript seg : activeSegments) {
            List<SlideHtmlSegmentAudio> audios = segmentAudioMapper.listByScript(seg.getId());
            readyAudios += audios.stream()
                    .filter(a -> "READY".equals(a.getStatus())).count();
            // P1-C(2026-08-09): 段级音频诚实 —— 存在 FAILED 段且无 READY → AUDIO_FAILED，
            // 而非一律 AUDIO_GENERATING（与 PPT 树一致）。
            if (audios.stream().anyMatch(a -> "FAILED".equals(a.getStatus()))) {
                anyFailed = true;
            }
        }
        tree.setAudioReadyCount(readyAudios);
        tree.setNarrationStatus(readyAudios > 0 ? "AUDIO_READY"
                : (anyFailed ? "AUDIO_FAILED" : "AUDIO_GENERATING"));
        return tree;
    }

    /**
     * G3-P1-C-1: 空树构建 —— 透传 course_slides 渲染状态。
     * <p>
     * 当课件树为空（无 PPT pages / 无 HTML unit / 无 v1 HTML）时，
     * 若存在对应 section 的 course_slides 记录且状态为 FAILED，
     * 前端可据此展示真实原因（如"PPT 文件损坏，请重新上传"），
     * 而非 90 秒轮询超时后只能提示泛化的"超时"（L0：错误消息诚实）。
     * </p>
     */
    private CoursewareTreeDTO emptyTree(Long courseId, Long sectionId) {
        CoursewareTreeDTO tree = new CoursewareTreeDTO();
        tree.setType("EMPTY");
        tree.setSectionId(sectionId);
        tree.setPages(Collections.emptyList());
        tree.setFlow(Collections.emptyList());
        tree.setNarrationStatus("PENDING");
        tree.setAudioReadyCount(0);
        // 透传最近一次上传的课件渲染状态（仅渲染失败/进行中时填充，空课件保持 null）
        if (courseSlideMapper != null && sectionId != null) {
            try {
                CourseSlide slide = courseSlideMapper.selectOne(
                        new LambdaQueryWrapper<CourseSlide>()
                                .eq(CourseSlide::getSectionId, sectionId)
                                .eq(courseId != null, CourseSlide::getCourseId, courseId)
                                .orderByDesc(CourseSlide::getId)
                                .last("LIMIT 1"));
                if (slide != null && slide.getStatus() != null) {
                    tree.setRenderStatus(renderStatusName(slide.getStatus()));
                    tree.setRenderErrorMessage(slide.getErrorMessage());
                }
            } catch (Exception e) {
                // 读失败仅丢失错误透传，不影响课件树主流程
                log.warn("[CoursewareTree] course_slides 渲染状态读取失败（跳过透传）: {}", e.getMessage());
            }
        }
        return tree;
    }

    /** 状态码 → 可读枚举名（未知码回退数字字符串，绝不让 EMPTY 树因枚举解析失败而 500）。 */
    private String renderStatusName(Integer status) {
        if (status == null) return null;
        try {
            return com.microcourse.enums.CourseSlideStatus.fromCode(status).name();
        } catch (Exception e) {
            return String.valueOf(status);
        }
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
        Long userId = SecurityUtil.getCurrentUserIdOpt();
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
                quizPassed = userId != null && exerciseRecordRepository.selectList(
                                new LambdaQueryWrapper<ExerciseRecord>()
                                        .eq(ExerciseRecord::getUserId, userId)
                                        .eq(ExerciseRecord::getExerciseId, quizId))
                        .stream().anyMatch(r -> Boolean.TRUE.equals(r.getPassed()));
                if (quizPassed == null) quizPassed = false;
            }
        }
        // G3-P0-5（P0-5 flow 端到端）：播放器不传 lastQuizId（无机制获取"最近完成的 quiz id"），
        // 导致 BRANCH_DEPENDS 规则永不匹配。此处服务端自动读取：当前用户在本 section 下
        // 最近一次通过（passed=true）的测验 → 注入 FlowContext.lastQuizId，让 BRANCH 规则真实生效。
        if (quizId == null && userId != null) {
            try {
                List<SectionQuiz> quizzes = sectionQuizMapper.selectList(
                        new LambdaQueryWrapper<SectionQuiz>()
                                .eq(SectionQuiz::getSectionId, sectionId));
                for (SectionQuiz q : quizzes) {
                    List<ExerciseRecord> recs = exerciseRecordRepository.selectList(
                            new LambdaQueryWrapper<ExerciseRecord>()
                                    .eq(ExerciseRecord::getUserId, userId)
                                    .eq(ExerciseRecord::getExerciseId, q.getId())
                                    .orderByDesc(ExerciseRecord::getSubmittedAt)
                                    .last("LIMIT 1"));
                    ExerciseRecord latest = recs.isEmpty() ? null : recs.get(0);
                    if (latest != null && Boolean.TRUE.equals(latest.getPassed())) {
                        quizId = q.getId();
                        quizPassed = true;
                        log.debug("[FlowEvaluate] BRANCH quiz 服务端读取: userId={}, section={}, quiz={}",
                                userId, sectionId, quizId);
                        break;
                    }
                }
            } catch (Exception e) {
                // 服务端读取失败 → 不命中 BRANCH（安全侧退化：宁可不跳，不伪造测验状态）
                log.warn("[FlowEvaluate] 最近通过测验读取失败（BRANCH 不命中）: {}", e.getMessage());
                quizId = null;
                quizPassed = null;
            }
        }
        // S-1（设计决策 3 完整性）：SKIP_IF_KNOWN 的 userProgress 也必须服务端读取，
        // 不信任客户端 request.getUserProgress()（可伪造进度绕过教师配置的 SKIP 规则）。
        // 数据源：learning_progress（user_id + course_id + lesson_id 最新记录），
        // video_progress 0-100 → 0.0-1.0；无记录 → null（SKIP 不命中，退化为线性）。
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

    @Override
    public String runGhostChapterFix() {
        // D-1 闭环 (V332)：与 V332 migration 一致的幂等自动修复。仅 ADMIN 可调用
        // （项目权限模型无 DBA 角色，等价生产 DBA 人工执行约束）。允许运维任意时刻
        // 重跑：先 SELECT 计数、确认非空才 UPDATE；修复事件写入 operation_logs。
        if (!SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION,
                    "仅 ADMIN 可执行幽灵章节自动修复（V332 幂等逻辑）");
        }
        try {
            jdbcTemplate.execute("""
                    DO $$
                    DECLARE
                        v_ppt_fixable  INT;
                        v_html_fixable INT;
                        v_ppt_fixed    INT;
                        v_html_fixed   INT;
                        v_review_left  INT;
                    BEGIN
                        SELECT COUNT(*) INTO v_ppt_fixable
                        FROM slide_ppt_pages p
                        JOIN course_sections cs ON cs.id = p.section_id
                        WHERE p.chapter_id = 1 AND cs.chapter_id IS DISTINCT FROM 1;

                        SELECT COUNT(*) INTO v_html_fixable
                        FROM slide_html_units u
                        JOIN course_sections cs ON cs.id = u.section_id
                        WHERE u.chapter_id = 1 AND cs.chapter_id IS DISTINCT FROM 1;

                        IF v_ppt_fixable > 0 THEN
                            UPDATE slide_ppt_pages p
                            SET chapter_id = cs.chapter_id, updated_at = NOW()
                            FROM course_sections cs
                            WHERE cs.id = p.section_id
                              AND p.chapter_id = 1
                              AND cs.chapter_id IS DISTINCT FROM 1;
                            GET DIAGNOSTICS v_ppt_fixed = ROW_COUNT;
                        ELSE
                            v_ppt_fixed := 0;
                        END IF;

                        IF v_html_fixable > 0 THEN
                            UPDATE slide_html_units u
                            SET chapter_id = cs.chapter_id, updated_at = NOW()
                            FROM course_sections cs
                            WHERE cs.id = u.section_id
                              AND u.chapter_id = 1
                              AND cs.chapter_id IS DISTINCT FROM 1;
                            GET DIAGNOSTICS v_html_fixed = ROW_COUNT;
                        ELSE
                            v_html_fixed := 0;
                        END IF;

                        SELECT COUNT(*) INTO v_review_left FROM v_ghost_chapter_audit;

                        IF v_ppt_fixed > 0 OR v_html_fixed > 0 OR v_review_left > 0 THEN
                            INSERT INTO operation_logs (user_id, action, target_type, target_id, detail, ip, success, created_at)
                            VALUES (NULL, 'GHOST_CHAPTER_FIX', 'SYSTEM', NULL,
                                jsonb_build_object('migration', 'V332', 'ppt_fixed', v_ppt_fixed,
                                    'html_fixed', v_html_fixed, 'review_left', v_review_left,
                                    'audited_at', NOW())::text,
                                NULL, TRUE, NOW());
                        END IF;
                    END;
                    $$""");
            // 修复完成后返回审计报告（对比 total_ghost_rows 变化）
            String report = jdbcTemplate.queryForObject(
                    "SELECT audit_ghost_chapters()::text", String.class);
            if (report == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "幽灵章节修复后审计返回空报告（audit_ghost_chapters() 未生效？）");
            }
            log.info("[GhostChapter-Fix] V332 幂等修复完成，修复后报告长度={} 字符", report.length());
            return report;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GhostChapter-Fix] V332 幂等修复执行失败", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "幽灵章节自动修复执行失败: " + e.getMessage()
                            + "。请确认 V328 诊断对象已应用（v_ghost_chapter_audit 视图存在）");
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

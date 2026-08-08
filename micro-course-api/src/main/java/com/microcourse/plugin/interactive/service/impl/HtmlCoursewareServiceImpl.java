package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.CourseSection;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.plugin.interactive.dto.SegmentDetectionResult;
import com.microcourse.plugin.interactive.dto.SegmentInfo;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import com.microcourse.plugin.interactive.service.HtmlSegmentDetector;
import com.microcourse.plugin.interactive.util.HtmlSanitizer;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * V303-V305 schema 配套 service 实现.
 *
 * 7-19 P0 防御:
 * <ul>
 *   <li>HtmlSanitizer.sanitizeForCourseware 在 createUnit + updateUnit 之前调用</li>
 *   <li>audio_token 用 UUID 32 字符, UK 校验</li>
 *   <li>saveSegmentScript 先 set active=false 旧 segment, 再 insert 新 active</li>
 *   <li>uk_html_units_section UNIQUE 保证一个 section 最多 1 个 unit</li>
 * </ul>
 */
@Service
public class HtmlCoursewareServiceImpl implements HtmlCoursewareService {

    private static final Logger log = LoggerFactory.getLogger(HtmlCoursewareServiceImpl.class);

    private final CourseSlideMapper courseSlideMapper;
    private final SlideHtmlUnitMapper unitMapper;
    private final SlideHtmlSegmentScriptMapper segmentScriptMapper;
    private final SlideHtmlSegmentAudioMapper segmentAudioMapper;
    private final CourseSectionRepository sectionRepo;
    private final CourseRepository courseRepository;  // Q-4: is_trusted 判定（课程 owner）
    /** P2-1: 自动分段检测（启发式标题/段落边界，Jsoup） */
    private final HtmlSegmentDetector segmentDetector;

    public HtmlCoursewareServiceImpl(CourseSlideMapper courseSlideMapper,
                                      SlideHtmlUnitMapper unitMapper,
                                      SlideHtmlSegmentScriptMapper segmentScriptMapper,
                                      SlideHtmlSegmentAudioMapper segmentAudioMapper,
                                      CourseSectionRepository sectionRepo,
                                      CourseRepository courseRepository,
                                      HtmlSegmentDetector segmentDetector) {
        this.courseSlideMapper = courseSlideMapper;
        this.unitMapper = unitMapper;
        this.segmentScriptMapper = segmentScriptMapper;
        this.segmentAudioMapper = segmentAudioMapper;
        this.sectionRepo = sectionRepo;
        this.courseRepository = courseRepository;
        this.segmentDetector = segmentDetector;
    }

    /**
     * Q-4: 判定 HTML 课件内容是否"可信教师上传"（当前用户是课程 owner 或 ADMIN）。
     * is_trusted=true → 宽松 sanitize（保留 script/style/onclick/iframe，安全依赖前端 sandbox）；
     * false → 严格 sanitize（移除所有 inline event handlers + 可执行标签）+ 读时 CSP nonce 防御。
     */
    private boolean computeIsTrusted(Long courseId) {
        if (courseId == null) {
            return false;
        }
        try {
            com.microcourse.entity.Course course = courseRepository.selectById(courseId);
            if (course == null) {
                return false;
            }
            return SecurityUtil.isOwnerOrAdmin(course.getTeacherId());
        } catch (Exception e) {
            log.warn("[HTML-Unit] is_trusted 判定失败 courseId={}（降级为不可信）: {}", courseId, e.getMessage());
            return false;
        }
    }

    // ====== Unit CRUD ======

    @Override
    @Transactional
    public Long createUnit(SlideHtmlUnitDTO dto) {
        // 7-19 P1-C 修复约束: 不破坏性 UPSERT
        // 如果已存在 unit, 走 updateUnit 而非 delete+insert
        SlideHtmlUnit existing = unitMapper.findBySection(dto.getSectionId());
        if (existing != null) {
            log.info("[HTML-Unit] UPSERT(in-place): section={} existing.id={}, updating...",
                    dto.getSectionId(), existing.getId());
            updateUnit(existing.getId(), dto);
            return existing.getId();
        }
        return createUnitFresh(dto);
    }

    private Long createUnitFresh(SlideHtmlUnitDTO dto) {
        if (dto.getHtmlContent() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "htmlContent is required");
        }
        if (dto.getSlideId() == null) {
            dto.setSlideId(resolveSlideId(dto));
        }
        // P1-C 修复：前端创建单元不传 chapterId，slide_html_units.chapter_id NOT NULL
        // 直接插入 → 500。从所属 slide（含 chapter_id/course_id）派生兜底。
        if (dto.getChapterId() == null && dto.getSlideId() != null) {
            CourseSlide slide = courseSlideMapper.selectById(dto.getSlideId());
            if (slide != null) {
                if (dto.getCourseId() == null) {
                    dto.setCourseId(slide.getCourseId());
                }
                Long chapterId = slide.getChapterId();
                // 【根因修复】课时级课件（section 上传）slide.chapter_id 为 NULL，
                // 若直接沿用则 slide_html_units.chapter_id NOT NULL 插入必然 500。
                // 从 section 反查其所属 chapter 兜底派生。
                if (chapterId == null && slide.getSectionId() != null) {
                    CourseSection sec = sectionRepo.selectById(slide.getSectionId());
                    if (sec != null) {
                        chapterId = sec.getChapterId();
                    }
                }
                dto.setChapterId(chapterId);
            }
        }
        // 7-19 P0 防御: HtmlSanitizer 必须 100% 调用
        // Q-4: 按 is_trusted 分流 sanitize（owner 教师 → 宽松；否则 → 严格移除 inline handlers/script）
        boolean isTrusted = computeIsTrusted(dto.getCourseId());
        String sanitized = HtmlSanitizer.sanitizeForCourseware(dto.getHtmlContent(), isTrusted);
        SlideHtmlUnit entity = new SlideHtmlUnit();
        // 【BUG #15 修复】 排除 id/createdAt/fileUuid, 避免前端伪造 fileUuid
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "fileUuid");
        entity.setHtmlSanitized(sanitized);
        entity.setIsTrusted(isTrusted);
        // 后端强制生成 fileUuid (不允许前端指定)
        entity.setFileUuid(UUID.randomUUID().toString().replace("-", ""));
        if (entity.getHasInteractions() == null) entity.setHasInteractions(false);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        unitMapper.insert(entity);
        // Q-4 审计: 信任教师上传记录（含 is_trusted 标记，便于追溯谁上传了可执行 HTML）
        log.info("[TrustAudit] F-2026-08-07-HTML: courseId={}, section={}, isTrusted={}, uploaderId={}, unitId={}",
                dto.getCourseId(), dto.getSectionId(), isTrusted,
                SecurityUtil.getCurrentUserIdOpt(), entity.getId());
        log.info("[HTML-Unit] created: id={}, section={}, slideId={}, fileSize={} bytes, sanitized",
                entity.getId(), entity.getSectionId(), entity.getSlideId(), entity.getFileSizeBytes());
        return entity.getId();
    }

    private Long resolveSlideId(SlideHtmlUnitDTO dto) {
        if (dto.getSectionId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "slideId is required when sectionId is missing");
        }
        LambdaQueryWrapper<CourseSlide> wrapper = new LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getSectionId, dto.getSectionId());
        if (dto.getCourseId() != null) {
            wrapper.eq(CourseSlide::getCourseId, dto.getCourseId());
        }
        wrapper.orderByDesc(CourseSlide::getUpdatedAt).last("LIMIT 1");
        CourseSlide slide = courseSlideMapper.selectOne(wrapper);
        if (slide == null || slide.getId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "slideId is required because no course slide exists for sectionId=" + dto.getSectionId());
        }
        log.info("[HTML-Unit] resolved slideId={} from sectionId={}", slide.getId(), dto.getSectionId());
        return slide.getId();
    }

    @Override
    public SlideHtmlUnitDTO getUnit(Long unitId) {
        SlideHtmlUnit entity = unitMapper.selectById(unitId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        return toUnitDTO(entity);
    }

    @Override
    public SlideHtmlUnitDTO getUnitBySection(Long sectionId) {
        SlideHtmlUnit entity = unitMapper.findBySection(sectionId);
        return entity == null ? null : toUnitDTO(entity);
    }

    @Override
    @Transactional
    public void updateUnit(Long unitId, SlideHtmlUnitDTO dto) {
        SlideHtmlUnit entity = unitMapper.selectById(unitId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        // 7-19 P0 防御: 即便 update, 也要 sanitize 新的 htmlContent
        // Q-4: 按 is_trusted 分流（owner 教师 → 宽松；否则 → 严格移除 inline handlers/script）
        boolean isTrusted = computeIsTrusted(entity.getCourseId());
        String newSanitized = entity.getHtmlSanitized();
        if (dto.getHtmlContent() != null && !dto.getHtmlContent().equals(entity.getHtmlContent())) {
            newSanitized = HtmlSanitizer.sanitizeForCourseware(dto.getHtmlContent(), isTrusted);
        }
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "sectionId", "fileUuid");
        entity.setHtmlSanitized(newSanitized);
        entity.setIsTrusted(isTrusted);
        entity.setUpdatedAt(LocalDateTime.now());
        int affected = unitMapper.updateById(entity);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "HTML unit updated concurrently, refresh and retry");
        }
        log.info("[TrustAudit] F-2026-08-07-HTML: courseId={}, section={}, isTrusted={}, uploaderId={}, unitId={}",
                entity.getCourseId(), entity.getSectionId(), isTrusted,
                SecurityUtil.getCurrentUserIdOpt(), unitId);
        log.info("[HTML-Unit] updated: id={}", unitId);
    }

    @Override
    @Transactional
    public void deleteUnit(Long unitId) {
        int affected = unitMapper.deleteById(unitId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        log.info("[HTML-Unit] deleted: id={}", unitId);
    }

    /**
     * P2-1 (F-2026-08-07-HTML-Detect): 自动分段检测。
     * - 校验当前用户是 unit 所属课程的 owner 或 ADMIN（G1 IDOR 协同，防跨课程检测/写入）
     * - 取 unit.htmlSanitized（缺失时回退 htmlContent）运行启发式检测
     * - 将段落数落库 slide_html_units.detected_segments（INT，1-50）
     * - 审计日志（谁在何时对哪个 unit 触发了检测）
     */
    @Override
    @Transactional
    public SegmentDetectionResult runDetection(Long unitId) {
        SlideHtmlUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        // G1 IDOR 协同：unit 必须属于当前用户可写的课程（owner/ADMIN），否则拒绝
        assertOwnerOfUnit(unit);

        String html = unit.getHtmlSanitized() != null ? unit.getHtmlSanitized() : unit.getHtmlContent();
        List<SegmentInfo> segments = segmentDetector.detectSegments(html);
        int count = segments.size();
        unit.setDetectedSegments(count);
        unit.setUpdatedAt(LocalDateTime.now());
        int affected = unitMapper.updateById(unit);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "HTML unit updated concurrently, refresh and retry");
        }
        // P2-1 审计：检测动作可追溯（谁触发、几个段、针对哪个 unit/课程）
        log.info("[TrustAudit] F-2026-08-07-HTML-Detect: courseId={}, unitId={}, teacherId={}, "
                        + "detectedSegments={}, uploaderId={}",
                unit.getCourseId(), unitId,
                resolveTeacherId(unit.getCourseId()),
                count, SecurityUtil.getCurrentUserIdOpt());
        log.info("[HTML-Unit] segment detection done: unitId={}, segments={}", unitId, count);
        return new SegmentDetectionResult(count, segments);
    }

    /**
     * G1 IDOR 协同：写操作前校验 unit 归属课程的 owner 或 ADMIN。
     * 与 SlideServiceImpl.uploadHtmlFile 的 owner 校验语义一致。
     */
    private void assertOwnerOfUnit(SlideHtmlUnit unit) {
        if (unit.getCourseId() == null) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "unit 缺少 courseId，无法校验归属");
        }
        Long teacherId = resolveTeacherId(unit.getCourseId());
        if (!SecurityUtil.isOwnerOrAdmin(teacherId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    private Long resolveTeacherId(Long courseId) {
        try {
            com.microcourse.entity.Course course = courseRepository.selectById(courseId);
            return course != null ? course.getTeacherId() : null;
        } catch (Exception e) {
            log.warn("[HTML-Unit] resolve teacherId failed courseId={}: {}", courseId, e.getMessage());
            return null;
        }
    }

    // ====== Segment Script 1:N 历史 ======

    @Override
    @Transactional
    public Long saveSegmentScript(Long unitId, Integer segmentIndex, String scriptText,
                                   String voice, String ttsModel, String segmentMarker,
                                   Long createdBy) {
        SlideHtmlUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        // 1. 降级当前 active segment
        SlideHtmlSegmentScript currentActive = segmentScriptMapper
                .findActiveByUnitAndIndex(unitId, segmentIndex);
        if (currentActive != null) {
            currentActive.setIsActive(false);
            currentActive.setUpdatedAt(LocalDateTime.now());
            segmentScriptMapper.updateById(currentActive);
        }
        // 2. 插入新 segment
        SlideHtmlSegmentScript next = new SlideHtmlSegmentScript();
        next.setHtmlUnitId(unitId);
        next.setSegmentIndex(segmentIndex);
        next.setSegmentMarker(segmentMarker);
        next.setScriptText(scriptText);
        next.setScriptVersion(currentActive != null ? currentActive.getScriptVersion() + 1 : 1);
        next.setIsActive(true);
        next.setVoice(voice);
        next.setTtsModel(ttsModel);
        LocalDateTime now = LocalDateTime.now();
        next.setCreatedAt(now);
        // F-2026-08-07-11：审计字段不信任客户端——createdBy 缺失时回退当前登录用户，
        // 否则 slide_html_segment_scripts.created_by NOT NULL 导致分段讲述稿保存必 500
        next.setCreatedBy(createdBy != null ? createdBy : SecurityUtil.getCurrentUserId());
        next.setUpdatedAt(now);
        segmentScriptMapper.insert(next);
        log.info("[HTML-Segment-Script] saved: id={}, unitId={}, segmentIndex={}, version={}",
                next.getId(), unitId, segmentIndex, next.getScriptVersion());
        return next.getId();
    }

    @Override
    public HtmlSegmentScriptDTO getActiveSegmentScript(Long unitId, Integer segmentIndex) {
        SlideHtmlSegmentScript entity = segmentScriptMapper
                .findActiveByUnitAndIndex(unitId, segmentIndex);
        return entity == null ? null : toSegmentScriptDTO(entity);
    }

    @Override
    public List<HtmlSegmentScriptDTO> listActiveSegments(Long unitId) {
        return segmentScriptMapper.listActiveByUnit(unitId).stream()
                .map(this::toSegmentScriptDTO).collect(Collectors.toList());
    }

    // ====== Segment Audio 1:N 音色 ======

    @Override
    @Transactional
    public Long generateSegmentAudio(Long segmentScriptId, String voice, String model, String ttsParams) {
        SlideHtmlSegmentScript script = segmentScriptMapper.selectById(segmentScriptId);
        if (script == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "Segment script not found: " + segmentScriptId);
        }
        SlideHtmlUnit unit = unitMapper.selectById(script.getHtmlUnitId());
        Long courseId = unit != null ? unit.getCourseId() : null;
        SlideHtmlSegmentAudio audio = new SlideHtmlSegmentAudio();
        audio.setSegmentScriptId(segmentScriptId);
        audio.setHtmlUnitId(script.getHtmlUnitId());
        audio.setSegmentIndex(script.getSegmentIndex());
        audio.setVoiceUsed(voice);
        audio.setModelUsed(model);
        audio.setGenerationParams(ttsParams);
        audio.setStatus("GENERATING");
        audio.setGenerationStartedAt(LocalDateTime.now());
        // 7-19 P1-C 兼容: audio_token UK, 流式 GET 不依赖 pageNumber
        audio.setAudioToken(UUID.randomUUID().toString().replace("-", ""));
        audio.setAudioUrl("/api/courses/" + courseId
                + "/courseware/audio/" + audio.getAudioToken());
        audio.setCreatedAt(LocalDateTime.now());
        segmentAudioMapper.insert(audio);
        log.info("[HTML-Segment-Audio] queued: id={}, segment={}, voice={}, token={}",
                audio.getId(), script.getSegmentIndex(), voice,
                audio.getAudioToken().substring(0, 8) + "...");
        return audio.getId();
    }

    @Override
    public List<HtmlSegmentAudioDTO> listSegmentAudios(Long segmentScriptId) {
        return segmentAudioMapper.listByScript(segmentScriptId).stream()
                .map(this::toSegmentAudioDTO).collect(Collectors.toList());
    }

    // ====== IDOR 对象级授权校验 (Phase 9 P0-2 修复) ======

    @Override
    public void verifyOwner(Long courseId) {
        com.microcourse.entity.Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            log.warn("[HTML-IDOR] 越权操作课程课件: courseId={}, userId={}, teacherId={}",
                    courseId, SecurityUtil.getCurrentUserIdOpt(), course.getTeacherId());
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该课程的课件");
        }
    }

    @Override
    public void verifyUnitOwner(Long courseId, Long unitId) {
        SlideHtmlUnit unit = unitMapper.selectById(unitId);
        if (unit == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "HTML unit not found: " + unitId);
        }
        if (!courseId.equals(unit.getCourseId())) {
            log.warn("[HTML-IDOR] unit 不属于该课程: path courseId={}, actual={}, unitId={}",
                    courseId, unit.getCourseId(), unitId);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该 HTML 课件");
        }
        verifyOwner(courseId);
    }

    @Override
    public void verifySectionUnitOwner(Long courseId, Long sectionId) {
        SlideHtmlUnit unit = unitMapper.findBySection(sectionId);
        if (unit != null && !courseId.equals(unit.getCourseId())) {
            log.warn("[HTML-IDOR] section 下 unit 不属于该课程: path courseId={}, actual={}, sectionId={}",
                    courseId, unit.getCourseId(), sectionId);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该 HTML 课件");
        }
        verifyOwner(courseId);
    }

    @Override
    public void verifySegmentScriptOwner(Long courseId, Long segmentScriptId) {
        SlideHtmlSegmentScript script = segmentScriptMapper.selectById(segmentScriptId);
        if (script == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "Segment script not found: " + segmentScriptId);
        }
        SlideHtmlUnit unit = unitMapper.selectById(script.getHtmlUnitId());
        if (unit == null || !courseId.equals(unit.getCourseId())) {
            log.warn("[HTML-IDOR] segment script 不属于该课程: path courseId={}, actual={}, scriptId={}",
                    courseId, unit != null ? unit.getCourseId() : null, segmentScriptId);
            throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作该 HTML 课件音频");
        }
        verifyOwner(courseId);
    }

    // ====== DTO converters ======

    private SlideHtmlUnitDTO toUnitDTO(SlideHtmlUnit e) {
        SlideHtmlUnitDTO d = new SlideHtmlUnitDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private HtmlSegmentScriptDTO toSegmentScriptDTO(SlideHtmlSegmentScript e) {
        HtmlSegmentScriptDTO d = new HtmlSegmentScriptDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private HtmlSegmentAudioDTO toSegmentAudioDTO(SlideHtmlSegmentAudio e) {
        HtmlSegmentAudioDTO d = new HtmlSegmentAudioDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }
}

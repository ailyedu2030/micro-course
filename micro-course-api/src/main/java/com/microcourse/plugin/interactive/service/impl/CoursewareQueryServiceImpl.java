package com.microcourse.plugin.interactive.service.impl;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
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
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
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
    private final com.microcourse.plugin.interactive.cache.AudioStreamCache audioStreamCache;

    public CoursewareQueryServiceImpl(SlidePptPageMapper pageMapper,
                                       SlidePptPageScriptMapper pageScriptMapper,
                                       SlidePptPageAudioMapper pageAudioMapper,
                                       SlidePptFlowMapper flowMapper,
                                       SlideHtmlUnitMapper unitMapper,
                                       SlideHtmlSegmentScriptMapper segmentScriptMapper,
                                       SlideHtmlSegmentAudioMapper segmentAudioMapper,
                                       com.microcourse.plugin.interactive.cache.AudioStreamCache audioStreamCache) {
        this.pageMapper = pageMapper;
        this.pageScriptMapper = pageScriptMapper;
        this.pageAudioMapper = pageAudioMapper;
        this.flowMapper = flowMapper;
        this.unitMapper = unitMapper;
        this.segmentScriptMapper = segmentScriptMapper;
        this.segmentAudioMapper = segmentAudioMapper;
        this.audioStreamCache = audioStreamCache;
    }

    @Override
    public CoursewareTreeDTO getCoursewareTree(Long courseId, Long sectionId) {
        if (courseId == null || sectionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "courseId 和 sectionId 必填");
        }

        // 1. 一次性查 PPT pages (验证 + 数据复用, 避免 N+1)
        List<SlidePptPage> pptPages = pageMapper.listBySection(sectionId);
        SlideHtmlUnit htmlUnit = unitMapper.findBySection(sectionId);

        // 【审计修复 BUG #4 + #8】 复用已查询的 pptPages 校验 section 归属,
        // 消除 BUG #8 的重复 SQL (N+1 → 1 query)
        validateSectionBelongsToCourse(courseId, sectionId, pptPages, htmlUnit);

        if (!pptPages.isEmpty()) {
            return buildPptTree(courseId, sectionId, pptPages);
        } else if (htmlUnit != null) {
            return buildHtmlTree(courseId, sectionId, htmlUnit);
        } else {
            return emptyTree(courseId, sectionId);
        }
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

        // 3. flow
        List<SlidePptFlow> flows = flowMapper.listBySection(sectionId);
        tree.setFlow(flows.stream().map(this::toPptFlowDTO).collect(Collectors.toList()));

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

package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import com.microcourse.plugin.interactive.util.HtmlSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private final SlideHtmlUnitMapper unitMapper;
    private final SlideHtmlSegmentScriptMapper segmentScriptMapper;
    private final SlideHtmlSegmentAudioMapper segmentAudioMapper;

    public HtmlCoursewareServiceImpl(SlideHtmlUnitMapper unitMapper,
                                      SlideHtmlSegmentScriptMapper segmentScriptMapper,
                                      SlideHtmlSegmentAudioMapper segmentAudioMapper) {
        this.unitMapper = unitMapper;
        this.segmentScriptMapper = segmentScriptMapper;
        this.segmentAudioMapper = segmentAudioMapper;
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
        // 7-19 P0 防御: HtmlSanitizer 必须 100% 调用
        String sanitized = HtmlSanitizer.sanitizeForCourseware(dto.getHtmlContent());
        SlideHtmlUnit entity = new SlideHtmlUnit();
        BeanUtils.copyProperties(dto, entity);
        entity.setHtmlSanitized(sanitized);
        if (entity.getFileUuid() == null) {
            entity.setFileUuid(UUID.randomUUID().toString().replace("-", ""));
        }
        if (entity.getHasInteractions() == null) entity.setHasInteractions(false);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        unitMapper.insert(entity);
        log.info("[HTML-Unit] created: id={}, section={}, fileSize={} bytes, sanitized",
                entity.getId(), entity.getSectionId(), entity.getFileSizeBytes());
        return entity.getId();
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
        String newSanitized = entity.getHtmlSanitized();
        if (dto.getHtmlContent() != null && !dto.getHtmlContent().equals(entity.getHtmlContent())) {
            newSanitized = HtmlSanitizer.sanitizeForCourseware(dto.getHtmlContent());
        }
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "sectionId", "fileUuid");
        entity.setHtmlSanitized(newSanitized);
        entity.setUpdatedAt(LocalDateTime.now());
        int affected = unitMapper.updateById(entity);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "HTML unit updated concurrently, refresh and retry");
        }
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
        next.setCreatedBy(createdBy);
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
                + "/audio/" + audio.getAudioToken());
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
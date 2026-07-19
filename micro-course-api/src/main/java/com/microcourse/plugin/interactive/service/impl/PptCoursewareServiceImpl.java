package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
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
 * V300-V302 + V306 schema 配套 service 实现.
 * <p>
 * 7-19 P0 防御:
 * <ul>
 *   <li>无 destructive UPSERT (insert/update 都是独立的, 不在 delete+insert 同事务)</li>
 *   <li>audio_token 用 UUID 32 字符, UK 校验, 不依赖 pageNumber</li>
 *   <li>每次 saveScript 先 set active=false 老脚本, 再 insert 新 active (避免 partial unique 冲突)</li>
 * </ul>
 */
@Service
public class PptCoursewareServiceImpl implements PptCoursewareService {

    private static final Logger log = LoggerFactory.getLogger(PptCoursewareServiceImpl.class);

    private final SlidePptPageMapper pageMapper;
    private final SlidePptPageScriptMapper scriptMapper;
    private final SlidePptPageAudioMapper audioMapper;
    private final SlidePptFlowMapper flowMapper;

    public PptCoursewareServiceImpl(SlidePptPageMapper pageMapper,
                                     SlidePptPageScriptMapper scriptMapper,
                                     SlidePptPageAudioMapper audioMapper,
                                     SlidePptFlowMapper flowMapper) {
        this.pageMapper = pageMapper;
        this.scriptMapper = scriptMapper;
        this.audioMapper = audioMapper;
        this.flowMapper = flowMapper;
    }

    // ====== Page CRUD ======

    @Override
    @Transactional
    public Long createPage(SlidePptPageDTO dto) {
        SlidePptPage entity = new SlidePptPage();
        // 【BUG #16 修复】 排除 id/createdAt/updatedAt, 避免前端覆盖主键和时间戳
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "updatedAt", "version");
        if (entity.getHasAnimation() == null) entity.setHasAnimation(false);
        if (entity.getHasEmbeddedMedia() == null) entity.setHasEmbeddedMedia(false);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        pageMapper.insert(entity);
        log.info("[PPT-Page] created: id={}, sectionId={}, pageNumber={}",
                entity.getId(), entity.getSectionId(), entity.getPageNumber());
        return entity.getId();
    }

    @Override
    public SlidePptPageDTO getPage(Long pageId) {
        SlidePptPage entity = pageMapper.selectById(pageId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "PPT page not found: " + pageId);
        }
        return toPageDTO(entity);
    }

    @Override
    public List<SlidePptPageDTO> listPagesBySection(Long sectionId) {
        List<SlidePptPage> pages = pageMapper.listBySection(sectionId);
        return pages.stream().map(this::toPageDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updatePage(Long pageId, SlidePptPageDTO dto) {
        SlidePptPage entity = pageMapper.selectById(pageId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "PPT page not found: " + pageId);
        }
        BeanUtils.copyProperties(dto, entity, "id", "createdAt", "slideId", "sectionId");
        entity.setUpdatedAt(LocalDateTime.now());
        int affected = pageMapper.updateById(entity);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "PPT page updated concurrently, refresh and retry");
        }
        log.info("[PPT-Page] updated: id={}", pageId);
    }

    @Override
    @Transactional
    public void deletePage(Long pageId) {
        int affected = pageMapper.deleteById(pageId);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "PPT page not found: " + pageId);
        }
        log.info("[PPT-Page] deleted: id={}", pageId);
    }

    // ====== Script 1:N 历史 ======

    @Override
    @Transactional
    public Long saveScript(Long pageId, String scriptText, String voice,
                           String ttsModel, Long createdBy) {
        // 1. 将当前 active 脚本降级
        SlidePptPageScript currentActive = scriptMapper.findActiveByPage(pageId);
        if (currentActive != null) {
            currentActive.setIsActive(false);
            currentActive.setUpdatedAt(LocalDateTime.now());
            scriptMapper.updateById(currentActive);
        }
        // 2. 插入新脚本 (active=true, version=current+1)
        SlidePptPageScript next = new SlidePptPageScript();
        next.setPptPageId(pageId);
        next.setScriptText(scriptText);
        next.setScriptVersion(currentActive != null ? currentActive.getScriptVersion() + 1 : 1);
        next.setIsActive(true);
        next.setVoice(voice);
        next.setTtsModel(ttsModel);
        LocalDateTime now = LocalDateTime.now();
        next.setCreatedAt(now);
        next.setCreatedBy(createdBy);
        next.setUpdatedAt(now);
        scriptMapper.insert(next);
        log.info("[PPT-Script] saved: id={}, pageId={}, version={}",
                next.getId(), pageId, next.getScriptVersion());
        return next.getId();
    }

    @Override
    public PptScriptDTO getActiveScript(Long pageId) {
        SlidePptPageScript entity = scriptMapper.findActiveByPage(pageId);
        return entity == null ? null : toScriptDTO(entity);
    }

    @Override
    public List<PptScriptDTO> listScriptHistory(Long pageId) {
        return scriptMapper.listHistoryByPage(pageId).stream()
                .map(this::toScriptDTO).collect(Collectors.toList());
    }

    // ====== Audio 1:N 音色 ======

    @Override
    @Transactional
    public Long generateAudio(Long scriptId, String voice, String model, String ttsParams) {
        SlidePptPageScript script = scriptMapper.selectById(scriptId);
        if (script == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "Script not found: " + scriptId);
        }
        SlidePptPage page = pageMapper.selectById(script.getPptPageId());
        Long courseId = page != null ? page.getCourseId() : null;
        SlidePptPageAudio audio = new SlidePptPageAudio();
        audio.setScriptId(scriptId);
        audio.setPptPageId(script.getPptPageId());
        audio.setVoiceUsed(voice);
        audio.setModelUsed(model);
        audio.setGenerationParams(ttsParams);
        audio.setStatus("GENERATING");
        audio.setGenerationStartedAt(LocalDateTime.now());
        // 7-19 P1-C 兼容: audio_token 是 UK, 流式 GET 不依赖 pageNumber
        audio.setAudioToken(UUID.randomUUID().toString().replace("-", ""));
        audio.setAudioUrl("/api/courses/" + courseId
                + "/audio/" + audio.getAudioToken());
        audio.setCreatedAt(LocalDateTime.now());
        audioMapper.insert(audio);
        log.info("[PPT-Audio] queued: id={}, scriptId={}, voice={}, token={}",
                audio.getId(), scriptId, voice,
                audio.getAudioToken().substring(0, 8) + "...");
        return audio.getId();
    }

    @Override
    public List<PptAudioDTO> listAudios(Long scriptId) {
        return audioMapper.listByScript(scriptId).stream()
                .map(this::toAudioDTO).collect(Collectors.toList());
    }

    @Override
    public PptAudioDTO getAudio(Long courseId, Long audioId) {
        SlidePptPageAudio entity = audioMapper.selectById(audioId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND, "Audio not found: " + audioId);
        }
        // 【BUG #17 修复】 校验 audio 归属 course (IDOR 防护)
        SlidePptPage page = pageMapper.selectById(entity.getPptPageId());
        if (page == null || !courseId.equals(page.getCourseId())) {
            log.warn("[PPT-Audio] IDOR ATTEMPT: path courseId={} actual courseId={}, audioId={}",
                    courseId, page != null ? page.getCourseId() : null, audioId);
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "Audio not in this course: audioId=" + audioId);
        }
        return toAudioDTO(entity);
    }

    // ====== Flow ======

    @Override
    @Transactional
    public Long createFlow(PptFlowDTO dto) {
        SlidePptFlow entity = new SlidePptFlow();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getPriority() == null) entity.setPriority(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        flowMapper.insert(entity);
        log.info("[PPT-Flow] created: id={}, type={}, from={}, to={}",
                entity.getId(), entity.getFlowType(),
                entity.getFromPageId(), entity.getToPageId());
        return entity.getId();
    }

    @Override
    public List<PptFlowDTO> listFlowsBySection(Long sectionId) {
        return flowMapper.listBySection(sectionId).stream()
                .map(this::toFlowDTO).collect(Collectors.toList());
    }

    // ====== DTO converters ======

    private SlidePptPageDTO toPageDTO(SlidePptPage e) {
        SlidePptPageDTO d = new SlidePptPageDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private PptScriptDTO toScriptDTO(SlidePptPageScript e) {
        PptScriptDTO d = new PptScriptDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private PptAudioDTO toAudioDTO(SlidePptPageAudio e) {
        PptAudioDTO d = new PptAudioDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }

    private PptFlowDTO toFlowDTO(SlidePptFlow e) {
        PptFlowDTO d = new PptFlowDTO();
        BeanUtils.copyProperties(e, d);
        return d;
    }
}
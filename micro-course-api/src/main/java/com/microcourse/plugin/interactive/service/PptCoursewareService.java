package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.dto.PptScriptDTO;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.PptFlowDTO;

import java.util.List;

/**
 * PPT 课件管理服务 (V300-V302 + V306 schema).
 *
 * 与遗留的 SlideService 不同, 本服务只处理新表 (slide_ppt_*).
 * 旧表 slide_pages 由 SlideService 维护, 3 个月保留期.
 */
public interface PptCoursewareService {

    // === 课件页面 CRUD ===
    Long createPage(SlidePptPageDTO dto);
    SlidePptPageDTO getPage(Long pageId);
    List<SlidePptPageDTO> listPagesBySection(Long sectionId);
    void updatePage(Long pageId, SlidePptPageDTO dto);
    void deletePage(Long pageId);

    // === 讲述稿 1:N 历史 ===
    Long saveScript(Long pageId, String scriptText, String voice, String ttsModel, Long createdBy);
    PptScriptDTO getActiveScript(Long pageId);
    List<PptScriptDTO> listScriptHistory(Long pageId);

    // === 音频 1:N 音色版本 ===
    Long generateAudio(Long scriptId, String voice, String model, String ttsParams);
    List<PptAudioDTO> listAudios(Long scriptId);
    PptAudioDTO getAudio(Long audioId);

    // === 页间跳转逻辑 ===
    Long createFlow(PptFlowDTO dto);
    List<PptFlowDTO> listFlowsBySection(Long sectionId);
}
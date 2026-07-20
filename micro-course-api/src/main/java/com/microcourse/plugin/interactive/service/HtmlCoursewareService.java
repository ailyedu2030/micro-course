package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;

import java.util.List;

/**
 * HTML 课件管理服务 (V303-V305 schema).
 * 一个 section 最多 1 个 HTML unit (uk_html_units_section UNIQUE).
 */
public interface HtmlCoursewareService {

    // === HTML 单元 CRUD ===
    Long createUnit(SlideHtmlUnitDTO dto);
    SlideHtmlUnitDTO getUnit(Long unitId);
    SlideHtmlUnitDTO getUnitBySection(Long sectionId);
    void updateUnit(Long unitId, SlideHtmlUnitDTO dto);
    void deleteUnit(Long unitId);

    // === HTML 分段脚本 1:N 历史 ===
    Long saveSegmentScript(Long unitId, Integer segmentIndex, String scriptText,
                           String voice, String ttsModel, String segmentMarker, Long createdBy);
    HtmlSegmentScriptDTO getActiveSegmentScript(Long unitId, Integer segmentIndex);
    List<HtmlSegmentScriptDTO> listActiveSegments(Long unitId);

    // === HTML 分段音频 1:N 音色 ===
    Long generateSegmentAudio(Long segmentScriptId, String voice, String model, String ttsParams);
    List<HtmlSegmentAudioDTO> listSegmentAudios(Long segmentScriptId);
}
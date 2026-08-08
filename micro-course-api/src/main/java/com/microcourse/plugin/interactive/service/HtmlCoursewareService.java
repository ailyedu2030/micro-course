package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.SegmentDetectionResult;

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

    /** P2-1: 对 unit 的 sanitize 后内容运行自动分段检测，落库 detected_segments 并返回段列表。 */
    SegmentDetectionResult runDetection(Long unitId);

    // === HTML 分段脚本 1:N 历史 ===
    Long saveSegmentScript(Long unitId, Integer segmentIndex, String scriptText,
                           String voice, String ttsModel, String segmentMarker, Long createdBy);
    HtmlSegmentScriptDTO getActiveSegmentScript(Long unitId, Integer segmentIndex);
    List<HtmlSegmentScriptDTO> listActiveSegments(Long unitId);

    // === HTML 分段音频 1:N 音色 ===
    Long generateSegmentAudio(Long segmentScriptId, String voice, String model, String ttsParams);
    List<HtmlSegmentAudioDTO> listSegmentAudios(Long segmentScriptId);

    // === IDOR 对象级授权校验 (Phase 9 P0-2 修复) ===
    // 所有写端点 + getUnit / getUnitBySection 读端点必须先通过对应 verify* 校验, 防止:
    // 1) 任意登录用户 GET /html/units/{unitId} 读取任意课程课件内容
    // 2) 非 owner TEACHER 凭自增 ID 枚举/篡改他人课程 HTML 课件
    // 3) 消耗他人 TTS 额度 (generateSegmentAudio 触发计费)
    // 语义: ADMIN 通行; TEACHER 必须为课程 owner (SecurityUtil.isOwnerOrAdmin)

    /** 校验当前用户是该课程的 owner (或 ADMIN)。courseId 不存在 → COURSE_NOT_FOUND(404)。 */
    void verifyOwner(Long courseId);

    /** 关键校验: unitId 必须属于 courseId (通过 SlideHtmlUnit.courseId 字段) + 当前用户是 owner。
     *  getUnit / updateUnit / deleteUnit / saveSegmentScript 使用。 */
    void verifyUnitOwner(Long courseId, Long unitId);

    /** 校验 section 下已存在的 unit 归属该课程 (unit 不存在视为无泄漏, 仍校验 owner)。
     *  createUnit / getUnitBySection 使用。 */
    void verifySectionUnitOwner(Long courseId, Long sectionId);

    /** 校验 segment script 所属 unit 属于该课程 + 当前用户是 owner。
     *  generateSegmentAudio (TTS 计费) 使用。 */
    void verifySegmentScriptOwner(Long courseId, Long segmentScriptId);
}
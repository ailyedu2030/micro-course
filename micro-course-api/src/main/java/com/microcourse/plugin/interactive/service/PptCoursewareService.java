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

    /**
     * 【BUG #17 修复 P0 IDOR】 按 courseId + audioId 查询, 校验 audio 归属 course.
     * 防止攻击者用合法 courseId 路径 + 任意 audioId 绕过 course 边界.
     */
    PptAudioDTO getAudio(Long courseId, Long audioId);

    // === 页间跳转逻辑 ===
    Long createFlow(PptFlowDTO dto);
    List<PptFlowDTO> listFlowsBySection(Long sectionId);
    void updateFlow(Long flowId, PptFlowDTO dto);
    void deleteFlow(Long flowId);

    // === IDOR 对象级授权校验 (Phase 9 P0-1 修复) ===
    // 所有写端点必须先通过对应 verify* 校验, 防止:
    // 1) 非 owner TEACHER 凭自增 ID 枚举/篡改他人课程 PPT 课件
    // 2) 跨课程修改/删除课件页 / 跳转规则
    // 3) 消耗他人 TTS 额度 (generateAudio 触发计费)
    // 语义: ADMIN 通行; TEACHER 必须为课程 owner (SecurityUtil.isOwnerOrAdmin)

    /** 校验当前用户是该课程的 owner (或 ADMIN)。courseId 不存在 → COURSE_NOT_FOUND(404)。 */
    void verifyOwner(Long courseId);

    /** 校验 section 属于该课程 + 当前用户是 owner。createPage / createFlow 使用。 */
    void verifySectionOwner(Long courseId, Long sectionId);

    /** 校验 page 属于该课程 + 当前用户是 owner。updatePage / deletePage / saveScript 使用。 */
    void verifyPageOwner(Long courseId, Long pageId);

    /** 校验 script 所属 page 属于该课程 + 当前用户是 owner。generateAudio (TTS 计费) 使用。 */
    void verifyScriptOwner(Long courseId, Long scriptId);

    /** 校验 flow 所属 section 属于该课程 + 当前用户是 owner。updateFlow / deleteFlow 使用。 */
    void verifyFlowOwner(Long courseId, Long flowId);
}

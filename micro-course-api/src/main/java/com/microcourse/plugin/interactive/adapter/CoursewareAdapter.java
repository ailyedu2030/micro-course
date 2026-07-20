package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;

import java.util.List;

/**
 * 课件统一接口 (spec 4.2)
 *
 * 双类型 (PPT / HTML) + 1 个 legacy 过渡实现, 通过 type() 区分.
 * 课件元数据/页面列表/内容/脚本/音频/状态 全部通过此接口访问,
 * 上层 (CQRS Query Service / Frontend) 不感知具体表结构.
 */
public interface CoursewareAdapter {

    /** 类型: "PPT" | "HTML" | "LEGACY" */
    String type();

    // ===== 课件元数据 =====
    CoursewareUnitMeta getUnitMeta(Long sectionId);

    // ===== 页面/段落列表 =====
    List<? extends CoursewareSegmentMeta> listSegments(Long sectionId);

    // ===== 内容获取 =====
    SegmentContent getSegmentContent(Long sectionId, Integer segmentIndex);

    // ===== 讲述稿 CRUD =====
    Object getActiveScript(Long segmentId);
    List<?> listScriptHistory(Long segmentId);
    Long saveNewScriptVersion(Long segmentId, String text, String voice, String ttsModel, Long createdBy);

    // ===== 音频 CRUD =====
    List<SegmentAudioVO> listAudios(Long scriptId);
    AudioStreamInfo resolveAudioToken(String token);
    Long generateAudio(Long scriptId, String voice, String model, String ttsParamsJson);

    // ===== 状态 =====
    String getStatus(Long segmentId);
}
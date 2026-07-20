package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.HtmlSegmentAudioDTO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentScriptDTO;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.dto.SlideHtmlUnitDTO;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML 课件适配器实现 (spec 4.2 / adapter/)
 *
 * 代理 slide_html_units + slide_html_segment_scripts + slide_html_segment_audios
 */
@Component
public class HtmlCoursewareAdapter implements CoursewareAdapter {

    private final HtmlCoursewareService htmlService;
    private final CoursewareQueryService queryService;

    @Autowired
    public HtmlCoursewareAdapter(HtmlCoursewareService htmlService,
                                  CoursewareQueryService queryService) {
        this.htmlService = htmlService;
        this.queryService = queryService;
    }

    @Override
    public String type() {
        return "HTML";
    }

    @Override
    public CoursewareUnitMeta getUnitMeta(Long sectionId) {
        SlideHtmlUnitDTO unit = htmlService.getUnitBySection(sectionId);
        return unit == null ? null : new HtmlUnitMeta(unit);
    }

    @Override
    public List<? extends CoursewareSegmentMeta> listSegments(Long sectionId) {
        SlideHtmlUnitDTO unit = htmlService.getUnitBySection(sectionId);
        if (unit == null) return List.of();
        List<HtmlSegmentScriptDTO> segs = htmlService.listActiveSegments(unit.getId());
        List<HtmlSegmentMeta> result = new ArrayList<>(segs == null ? 0 : segs.size());
        if (segs != null) {
            for (HtmlSegmentScriptDTO s : segs) {
                result.add(new HtmlSegmentMeta(s));
            }
        }
        return result;
    }

    @Override
    public SegmentContent getSegmentContent(Long sectionId, Integer segmentIndex) {
        SlideHtmlUnitDTO unit = htmlService.getUnitBySection(sectionId);
        if (unit == null) return null;
        return new HtmlSegmentContent(unit);
    }

    @Override
    public Object getActiveScript(Long segmentId) {
        // segmentId 形参语义: 这里传入的是 (unitId*1000+segmentIndex) 或 scriptId
        // HtmlCoursewareService 提供 getActiveSegmentScript(unitId, segmentIndex)
        // 简化: 假定 caller 已用 HtmlCoursewareService.getActiveSegmentScript 取过, 此处不再二次解析
        return null;
    }

    @Override
    public List<?> listScriptHistory(Long segmentId) {
        // 历史由 queryService 在 CQRS tree 中聚合
        return List.of();
    }

    @Override
    public Long saveNewScriptVersion(Long segmentId, String text, String voice, String ttsModel, Long createdBy) {
        // segmentId 形参语义为 "unitId*1000+segmentIndex" 不可行, 改为调用方解析
        // 实际使用应通过 HtmlCoursewareService.saveSegmentScript(unitId, segmentIndex, ...)
        // 这里只占位返回 0, 真实调用走 controller
        return 0L;
    }

    @Override
    public List<SegmentAudioVO> listAudios(Long scriptId) {
        List<HtmlSegmentAudioDTO> dtos = htmlService.listSegmentAudios(scriptId);
        List<SegmentAudioVO> result = new ArrayList<>(dtos == null ? 0 : dtos.size());
        if (dtos != null) {
            for (HtmlSegmentAudioDTO d : dtos) {
                result.add(SegmentAudioVO.fromHtml(d));
            }
        }
        return result;
    }

    @Override
    public AudioStreamInfo resolveAudioToken(String token) {
        return queryService.resolveAudioToken(token);
    }

    @Override
    public Long generateAudio(Long scriptId, String voice, String model, String ttsParamsJson) {
        return htmlService.generateSegmentAudio(scriptId, voice, model, ttsParamsJson);
    }

    @Override
    public String getStatus(Long segmentId) {
        // segmentId 在此为 scriptId. status 由音频列表聚合, 此处返回 ACTIVE 兜底
        return "ACTIVE";
    }

    // ===== Inner classes =====

    private static class HtmlUnitMeta implements CoursewareUnitMeta {
        private final SlideHtmlUnitDTO unit;
        HtmlUnitMeta(SlideHtmlUnitDTO unit) { this.unit = unit; }
        public Long getId() { return unit.getId(); }
        public Long getSectionId() { return unit.getSectionId(); }
        public Long getCourseId() { return unit.getCourseId(); }
        public String getTitle() { return unit.getPageTitle(); }
    }

    private static class HtmlSegmentMeta implements CoursewareSegmentMeta {
        private final HtmlSegmentScriptDTO seg;
        HtmlSegmentMeta(HtmlSegmentScriptDTO seg) { this.seg = seg; }
        public Long getSegmentId() { return seg.getId(); }
        public Integer getSegmentIndex() { return seg.getSegmentIndex(); }
        public String getTitle() { return null; }
        public String getStatus() { return "ACTIVE"; }
    }

    private static class HtmlSegmentContent implements SegmentContent {
        private final SlideHtmlUnitDTO unit;
        HtmlSegmentContent(SlideHtmlUnitDTO unit) { this.unit = unit; }
        public String getContentType() { return "HTML"; }
        public String getImageUrl() { return null; }
        public Integer getImageWidth() { return null; }
        public Integer getImageHeight() { return null; }
        public String getHtmlContent() { return unit.getHtmlSanitized(); }
    }
}
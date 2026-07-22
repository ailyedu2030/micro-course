package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.PptAudioDTO;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.dto.SlidePptPageDTO;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * PPT 课件适配器实现 (spec 4.2 / adapter/)
 *
 * 代理 slide_ppt_pages + slide_ppt_page_scripts + slide_ppt_page_audios + slide_ppt_flow
 * 暴露为统一 CoursewareAdapter 接口, 上层不感知表结构.
 */
@Component
public class PptCoursewareAdapter implements CoursewareAdapter {

    private final PptCoursewareService pptService;
    private final CoursewareQueryService queryService;

    @Autowired
    public PptCoursewareAdapter(PptCoursewareService pptService,
                                 CoursewareQueryService queryService) {
        this.pptService = pptService;
        this.queryService = queryService;
    }

    @Override
    public String type() {
        return "PPT";
    }

    @Override
    public CoursewareUnitMeta getUnitMeta(Long sectionId) {
        List<SlidePptPageDTO> pages = pptService.listPagesBySection(sectionId);
        if (pages.isEmpty()) {
            return null;
        }
        SlidePptPageDTO first = pages.get(0);
        return new PptUnitMeta(first);
    }

    @Override
    public List<? extends CoursewareSegmentMeta> listSegments(Long sectionId) {
        List<SlidePptPageDTO> pages = pptService.listPagesBySection(sectionId);
        List<PptSegmentMeta> result = new ArrayList<>(pages.size());
        for (SlidePptPageDTO p : pages) {
            result.add(new PptSegmentMeta(p));
        }
        return result;
    }

    @Override
    public SegmentContent getSegmentContent(Long sectionId, Integer segmentIndex) {
        List<SlidePptPageDTO> pages = pptService.listPagesBySection(sectionId);
        for (SlidePptPageDTO p : pages) {
            if (p.getPageNumber() != null && p.getPageNumber().equals(segmentIndex)) {
                return new PptSegmentContent(p);
            }
        }
        return null;
    }

    @Override
    public Object getActiveScript(Long segmentId) {
        return pptService.getActiveScript(segmentId);
    }

    @Override
    public List<?> listScriptHistory(Long segmentId) {
        return pptService.listScriptHistory(segmentId);
    }

    @Override
    public Long saveNewScriptVersion(Long segmentId, String text, String voice, String ttsModel, Long createdBy) {
        return pptService.saveScript(segmentId, text, voice, ttsModel, createdBy);
    }

    @Override
    public List<SegmentAudioVO> listAudios(Long scriptId) {
        List<PptAudioDTO> dtos = pptService.listAudios(scriptId);
        List<SegmentAudioVO> result = new ArrayList<>(dtos.size());
        for (PptAudioDTO d : dtos) {
            result.add(SegmentAudioVO.fromPpt(d));
        }
        return result;
    }

    @Override
    public AudioStreamInfo resolveAudioToken(String token) {
        return queryService.resolveAudioToken(token);
    }

    @Override
    public Long generateAudio(Long scriptId, String voice, String model, String ttsParamsJson) {
        return pptService.generateAudio(scriptId, voice, model, ttsParamsJson);
    }

    @Override
    public String getStatus(Long segmentId) {
        // 状态由 v_slide_ppt_page_status 视图聚合, 此处只返回 ACTIVE 兜底
        return "ACTIVE";
    }

    // ===== Inner classes (PPT 元数据实现) =====

    private static class PptUnitMeta implements CoursewareUnitMeta {
        private final SlidePptPageDTO page;
        PptUnitMeta(SlidePptPageDTO page) { this.page = page; }
        public Long getId() { return page.getId(); }
        public Long getSectionId() { return page.getSectionId(); }
        public Long getCourseId() { return page.getCourseId(); }
        public String getTitle() { return page.getPageTitle(); }
    }

    private static class PptSegmentMeta implements CoursewareSegmentMeta {
        private final SlidePptPageDTO page;
        PptSegmentMeta(SlidePptPageDTO page) { this.page = page; }
        public Long getSegmentId() { return page.getId(); }
        public Integer getSegmentIndex() { return page.getPageNumber(); }
        public String getTitle() { return page.getPageTitle(); }
        public String getStatus() { return "ACTIVE"; }
    }

    private static class PptSegmentContent implements SegmentContent {
        private final SlidePptPageDTO page;
        PptSegmentContent(SlidePptPageDTO page) { this.page = page; }
        public String getContentType() { return "IMAGE"; }
        public String getImageUrl() { return page.getImageUrl(); }
        public Integer getImageWidth() { return page.getImageWidth(); }
        public Integer getImageHeight() { return page.getImageHeight(); }
        public String getHtmlContent() { return null; }
    }
}

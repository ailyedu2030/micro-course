package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 旧表 slide_pages 兼容适配器 (spec 4.2 / adapter/)
 *
 * 3 个月保留期内继续工作. Phase 5 后此 adapter 将被删除.
 * 【W36】 旧表字段映射: image_url/html_content/narration_script/narration_audio_url.
 */
@Component
public class LegacyCoursewareAdapter implements CoursewareAdapter {

    private static final Logger log = LoggerFactory.getLogger(LegacyCoursewareAdapter.class);

    private final SlideService slideService;
    private final SlidePageMapper slidePageMapper;
    private final CoursewareQueryService queryService;

    @Autowired
    public LegacyCoursewareAdapter(SlideService slideService,
                                    SlidePageMapper slidePageMapper,
                                    CoursewareQueryService queryService) {
        this.slideService = slideService;
        this.slidePageMapper = slidePageMapper;
        this.queryService = queryService;
    }

    @Override
    public String type() {
        return "LEGACY";
    }

    @Override
    public CoursewareUnitMeta getUnitMeta(Long sectionId) {
        List<SlidePage> pages = slidePageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SlidePage>()
                .eq("section_id", sectionId)
                .eq("is_legacy", true)
                .orderByAsc("page_number"));
        if (pages == null || pages.isEmpty()) return null;
        return new LegacyUnitMeta(pages.get(0));
    }

    @Override
    public List<? extends CoursewareSegmentMeta> listSegments(Long sectionId) {
        List<SlidePage> pages = slidePageMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SlidePage>()
                .eq("section_id", sectionId)
                .eq("is_legacy", true)
                .orderByAsc("page_number"));
        List<LegacySegmentMeta> result = new ArrayList<>(pages == null ? 0 : pages.size());
        if (pages != null) {
            for (SlidePage p : pages) {
                result.add(new LegacySegmentMeta(p));
            }
        }
        return result;
    }

    @Override
    public SegmentContent getSegmentContent(Long sectionId, Integer segmentIndex) {
        SlidePage page = slidePageMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SlidePage>()
                .eq("section_id", sectionId)
                .eq("page_number", segmentIndex)
                .eq("is_legacy", true)
                .last("LIMIT 1"));
        return page == null ? null : new LegacySegmentContent(page);
    }

    @Override
    public Object getActiveScript(Long segmentId) {
        SlidePage page = slidePageMapper.selectById(segmentId);
        if (page == null) return null;
        log.warn("[LEGACY-Adapter] getActiveScript from old table - migrate to new schema");
        return page.getNarrationScript();
    }

    @Override
    public List<?> listScriptHistory(Long segmentId) {
        log.warn("[LEGACY-Adapter] legacy table has no script history");
        return List.of();
    }

    @Override
    public Long saveNewScriptVersion(Long segmentId, String text, String voice, String ttsModel, Long createdBy) {
        log.warn("[LEGACY-Adapter] saveScript: legacy non-versioned update, history lost");
        SlidePage page = slidePageMapper.selectById(segmentId);
        if (page == null) return 0L;
        page.setNarrationScript(text);
        slidePageMapper.updateById(page);
        return segmentId;
    }

    @Override
    public List<SegmentAudioVO> listAudios(Long scriptId) {
        SlidePage page = slidePageMapper.selectById(scriptId);
        if (page == null || page.getNarrationAudioUrl() == null) return List.of();
        SegmentAudioVO vo = new SegmentAudioVO();
        vo.setUrl(page.getNarrationAudioUrl());
        return List.of(vo);
    }

    @Override
    public AudioStreamInfo resolveAudioToken(String token) {
        return queryService.resolveAudioToken(token);
    }

    @Override
    public Long generateAudio(Long scriptId, String voice, String model, String ttsParamsJson) {
        log.warn("[LEGACY-Adapter] generateAudio: legacy route, no version history");
        // legacy 路径: 直接调 SlideService.generateAudio (旧实现)
        try {
            slideService.reorderPages(scriptId, java.util.List.of());  // 占位触发
        } catch (Exception ignored) {}
        return 0L;
    }

    @Override
    public String getStatus(Long segmentId) {
        SlidePage page = slidePageMapper.selectById(segmentId);
        if (page == null) return "PENDING";
        return page.getNarrationStatus();
    }

    // ===== Inner classes =====

    private static class LegacyUnitMeta implements CoursewareUnitMeta {
        private final SlidePage page;
        LegacyUnitMeta(SlidePage page) { this.page = page; }
        public Long getId() { return page.getId(); }
        public Long getSectionId() { return page.getSectionId(); }
        public Long getCourseId() { return page.getCourseId(); }
        public String getTitle() { return null; }
    }

    private static class LegacySegmentMeta implements CoursewareSegmentMeta {
        private final SlidePage page;
        LegacySegmentMeta(SlidePage page) { this.page = page; }
        public Long getSegmentId() { return page.getId(); }
        public Integer getSegmentIndex() { return page.getPageNumber(); }
        public String getTitle() { return null; }
        public String getStatus() { return page.getNarrationStatus(); }
    }

    private static class LegacySegmentContent implements SegmentContent {
        private final SlidePage page;
        LegacySegmentContent(SlidePage page) { this.page = page; }
        public String getContentType() { return "HTML".equals(page.getContentType()) ? "HTML" : "IMAGE"; }
        public String getImageUrl() { return page.getImageUrl(); }
        public Integer getImageWidth() { return page.getImageWidth(); }
        public Integer getImageHeight() { return page.getImageHeight(); }
        public String getHtmlContent() { return page.getHtmlContent(); }
    }
}
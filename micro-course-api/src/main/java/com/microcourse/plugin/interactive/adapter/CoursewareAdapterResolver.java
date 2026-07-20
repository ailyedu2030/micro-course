package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.dto.AudioStreamInfo;
import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 适配器解析器 (spec 4.2 / adapter/)
 *
 * 单例 bean, 维护 sectionId → CoursewareAdapter 的路由:
 *   1. 优先 PPT (slide_ppt_pages 有数据)
 *   2. 其次 HTML (slide_html_units 有数据)
 *   3. 兜底 Legacy (slide_pages)
 *
 * 与 AudioStreamInfo 的解析独立, 音频 token 路由走 queryService.
 */
@Component
public class CoursewareAdapterResolver {

    private final PptCoursewareAdapter pptAdapter;
    private final HtmlCoursewareAdapter htmlAdapter;
    private final LegacyCoursewareAdapter legacyAdapter;
    private final CoursewareQueryService queryService;

    @Autowired
    public CoursewareAdapterResolver(PptCoursewareAdapter pptAdapter,
                                       HtmlCoursewareAdapter htmlAdapter,
                                       LegacyCoursewareAdapter legacyAdapter,
                                       CoursewareQueryService queryService) {
        this.pptAdapter = pptAdapter;
        this.htmlAdapter = htmlAdapter;
        this.legacyAdapter = legacyAdapter;
        this.queryService = queryService;
    }

    /**
     * 根据 sectionId 解析合适的 adapter
     * @return non-null adapter (legacy 兜底)
     */
    public CoursewareAdapter resolve(Long sectionId) {
        if (sectionId == null) return legacyAdapter;
        if (pptAdapter.getUnitMeta(sectionId) != null) return pptAdapter;
        if (htmlAdapter.getUnitMeta(sectionId) != null) return htmlAdapter;
        return legacyAdapter;
    }

    /**
     * 按 type 解析 (用于 Controller 显式路由)
     */
    public CoursewareAdapter resolveByType(String type) {
        if (type == null) return legacyAdapter;
        return switch (type.toUpperCase()) {
            case "PPT" -> pptAdapter;
            case "HTML" -> htmlAdapter;
            default -> legacyAdapter;
        };
    }

    /**
     * 委托查询音频 token (adapter 间共享)
     */
    public AudioStreamInfo delegateResolveAudioToken(String token) {
        return queryService.resolveAudioToken(token);
    }

    /**
     * 列出全部 adapter (诊断用)
     */
    public Map<String, CoursewareAdapter> all() {
        Map<String, CoursewareAdapter> m = new HashMap<>();
        m.put("PPT", pptAdapter);
        m.put("HTML", htmlAdapter);
        m.put("LEGACY", legacyAdapter);
        return m;
    }

    public List<CoursewareAdapter> list() {
        return List.of(pptAdapter, htmlAdapter, legacyAdapter);
    }
}
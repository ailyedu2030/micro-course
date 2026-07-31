package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.service.SectionSlideService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}")
public class SectionSlideController {

    private final SectionSlideService sectionSlideService;

    public SectionSlideController(SectionSlideService sectionSlideService) {
        this.sectionSlideService = sectionSlideService;
    }

    /**
     * GET /api/courses/{courseId}/sections/{sectionId}/slide
     * 返回该课时上传的课件页面列表。
     * 同时支持 JWT（@PreAuthorize）和 Hermes API Key（X-API-Key 头）鉴权。
     */
    @GetMapping("/slide")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<SlidePageVO>> getSectionSlide(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        List<SlidePageVO> vos = sectionSlideService.getSectionSlide(courseId, sectionId, apiKey);
        return R.ok(vos);
    }
}

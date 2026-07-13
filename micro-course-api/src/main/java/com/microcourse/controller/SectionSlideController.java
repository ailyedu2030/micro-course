package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}")
public class SectionSlideController {

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;

    public SectionSlideController(CourseSlideMapper courseSlideMapper,
                                  SlidePageMapper slidePageMapper) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
    }

    /**
     * GET /api/courses/{courseId}/sections/{sectionId}/slide
     * 返回该课时上传的课件页面列表（与 content_url 配合使用）
     */
    @GetMapping("/slide")
    @PreAuthorize("isAuthenticated()")
    public R<List<com.microcourse.plugin.interactive.dto.SlidePageVO>> getSectionSlide(
            @PathVariable Long sectionId) {
        // 查该课时的 slide
        CourseSlide slide = courseSlideMapper.selectOne(
                new LambdaQueryWrapper<CourseSlide>()
                        .eq(CourseSlide::getSectionId, sectionId));
        if (slide == null) {
            return R.ok(List.of());
        }
        // 查该 slide 的页面
        List<SlidePage> pages = slidePageMapper.selectList(
                new LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getSlideId, slide.getId())
                        .orderByAsc(SlidePage::getPageNumber));
        // 转为 DTO
        List<com.microcourse.plugin.interactive.dto.SlidePageVO> vos = pages.stream()
                .map(p -> {
                    com.microcourse.plugin.interactive.dto.SlidePageVO vo =
                            new com.microcourse.plugin.interactive.dto.SlidePageVO();
                    vo.setId(p.getId());
                    vo.setSlideId(p.getSlideId());
                    vo.setSectionId(p.getSectionId());
                    vo.setChapterId(p.getChapterId());
                    vo.setCourseId(p.getCourseId());
                    vo.setPageNumber(p.getPageNumber());
                    vo.setImageUrl(p.getImageUrl());
                    vo.setThumbnailUrl(p.getThumbnailUrl());
                    vo.setContentType(p.getContentType());
                    vo.setHtmlContent(p.getHtmlContent());
                    vo.setNarrationStatus(p.getNarrationStatus());
                    vo.setNarrationAudioUrl(p.getNarrationAudioUrl());
                    vo.setAudioDuration(p.getAudioDuration());
                    vo.setCreatedAt(p.getCreatedAt());
                    vo.setUpdatedAt(p.getUpdatedAt());
                    return vo;
                })
                .toList();
        return R.ok(vos);
    }
}

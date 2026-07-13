package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.repository.UserRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/courses/{courseId}/sections/{sectionId}")
public class SectionSlideController {

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final UserRepository userRepository;

    public SectionSlideController(CourseSlideMapper courseSlideMapper,
                                  SlidePageMapper slidePageMapper,
                                  UserRepository userRepository) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/courses/{courseId}/sections/{sectionId}/slide
     * 返回该课时上传的课件页面列表。
     * 同时支持 JWT（@PreAuthorize）和 Hermes API Key（X-API-Key 头）鉴权。
     */
    @GetMapping("/slide")
    @PreAuthorize("isAuthenticated()")
    public R<List<com.microcourse.plugin.interactive.dto.SlidePageVO>> getSectionSlide(
            @PathVariable Long sectionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        // API Key 鉴权（Hermes 使用）
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<User> caller = userRepository.findByApiKey(apiKey);
            if (caller.isEmpty()) {
                throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
            }
            // API Key 验证通过，继续处理
        }
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

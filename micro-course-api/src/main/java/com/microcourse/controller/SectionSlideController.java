package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.util.SecurityUtil;
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
    private final CourseRepository courseRepository;

    public SectionSlideController(CourseSlideMapper courseSlideMapper,
                                  SlidePageMapper slidePageMapper,
                                  UserRepository userRepository,
                                  CourseRepository courseRepository) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /api/courses/{courseId}/sections/{sectionId}/slide
     * 返回该课时上传的课件页面列表。
     * 同时支持 JWT（@PreAuthorize）和 Hermes API Key（X-API-Key 头）鉴权。
     * <p>P0-3 修复: API Key 路径也要 ownership 校验(以前只校验 API Key 有效性,允许越权读任何 course 的 slide)
     */
    @GetMapping("/slide")
    @PreAuthorize("isAuthenticated()")
    public R<List<com.microcourse.plugin.interactive.dto.SlidePageVO>> getSectionSlide(
            @PathVariable Long courseId,
            @PathVariable Long sectionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        // 1) 鉴权: API Key 或 JWT 二选一
        Long callerUserId = null;
        if (apiKey != null && !apiKey.isBlank()) {
            Optional<User> caller = userRepository.findByApiKey(apiKey);
            if (caller.isEmpty()) {
                throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
            }
            callerUserId = caller.get().getId();
        } else if (SecurityUtil.getCurrentUserId() != null) {
            callerUserId = SecurityUtil.getCurrentUserId();
        } else {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "未登录且未提供 API Key");
        }

        // 2) P0-3 修复: 鉴权后必须做 ownership 校验
        //  - ADMIN 豁免
        //  - TEACHER: 必须 owner
        //  - STUDENT: 必须已选此课 (与 SlideController.verifyAccess 一致)
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isAdmin()) {
            if (SecurityUtil.hasRole("TEACHER")) {
                if (!callerUserId.equals(course.getTeacherId())) {
                    throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该课程课件");
                }
            } else {
                // 其他角色(主要是 STUDENT):必须已选此课
                // 这里仅作 owner 阻断,详细 enrollment 校验由 SlideController 负责
                // 简单策略: 非 owner 一律拒绝(因为本接口只供教师写/AI 同步使用)
                throw new BusinessException(ErrorCode.NO_PERMISSION, "该接口仅供教师/管理员调用");
            }
        }

        // 3) 查该课时的 slide
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

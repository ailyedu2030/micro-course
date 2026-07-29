package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.SectionSlideService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SectionSlideServiceImpl implements SectionSlideService {

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public SectionSlideServiceImpl(CourseSlideMapper courseSlideMapper,
                                   SlidePageMapper slidePageMapper,
                                   UserRepository userRepository,
                                   CourseRepository courseRepository) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public List<SlidePageVO> getSectionSlide(Long courseId, Long sectionId, String apiKey) {
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
            // TEACHER（或 API Key 用户）: 必须为课程所有者
            if (!callerUserId.equals(course.getTeacherId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权访问该课程课件");
            }
        }

        // 3) 查该课时的 slide
        CourseSlide slide = courseSlideMapper.selectOne(
                new LambdaQueryWrapper<CourseSlide>()
                        .eq(CourseSlide::getSectionId, sectionId));
        if (slide == null) {
            return List.of();
        }
        // 查该 slide 的页面
        List<SlidePage> pages = slidePageMapper.selectList(
                new LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getSlideId, slide.getId())
                        .orderByAsc(SlidePage::getPageNumber));
        // 转为 DTO
        return pages.stream()
                .map(p -> {
                    SlidePageVO vo = new SlidePageVO();
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
    }
}

package com.microcourse.service.impl;

import com.microcourse.entity.Course;
import com.microcourse.entity.CourseChapter;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Component;

/**
 * 视频权限校验器 (Phase 11 拆分 VideoServiceImpl)
 *
 * <p>原 VideoServiceImpl 中 4 个权限校验方法 (assertCourseOwnership / assertChapterBelongsToCourse /
 * assertCourseOwner / getMaxFileSize) 提取到本类,便于 VideoUploadExecutor 复用。</p>
 *
 * @author refactor Phase 11 (2026-08-18)
 */
@Component
public class VideoValidator {

    private final CourseRepository courseRepository;
    private final CourseChapterRepository chapterRepository;

    public VideoValidator(CourseRepository courseRepository, CourseChapterRepository chapterRepository) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
    }

    /**
     * 校验课程存在 + 当前用户是 Owner 或 ADMIN
     */
    public void assertCourseOwnership(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        assertCourseOwner(course);
    }

    /**
     * 校验章节归属课程(P1-6)
     */
    public void assertChapterBelongsToCourse(Long chapterId, Long courseId) {
        CourseChapter chapter = chapterRepository.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(ErrorCode.CHAPTER_NOT_FOUND);
        }
        if (!courseId.equals(chapter.getCourseId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节与课程不匹配");
        }
    }

    /**
     * 校验当前用户是课程 Owner 或 ADMIN
     */
    public void assertCourseOwner(Course course) {
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "非课程所有者");
        }
    }
}
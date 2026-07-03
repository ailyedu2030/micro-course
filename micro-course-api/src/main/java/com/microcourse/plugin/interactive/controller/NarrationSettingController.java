package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.narration.NarrationSettingRequest;
import com.microcourse.dto.narration.NarrationSettingVO;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.NarrationSettingService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/narration-settings")
public class NarrationSettingController {

    private final NarrationSettingService narrationSettingService;
    private final CourseRepository courseRepository;

    public NarrationSettingController(NarrationSettingService narrationSettingService,
                                      CourseRepository courseRepository) {
        this.narrationSettingService = narrationSettingService;
        this.courseRepository = courseRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NarrationSettingVO> get(@PathVariable Long courseId) {
        verifyCourseOwner(courseId);
        return R.ok(narrationSettingService.getByCourseId(courseId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<NarrationSettingVO> update(@PathVariable Long courseId,
                                         @Valid @RequestBody NarrationSettingRequest request) {
        verifyCourseOwner(courseId);
        return R.ok(narrationSettingService.save(courseId, request));
    }

    private void verifyCourseOwner(Long courseId) {
        if (SecurityUtil.isAdmin()) return;
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }
}

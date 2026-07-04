package com.microcourse.plugin.interactive.controller;

import com.microcourse.dto.R;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.service.TtsService;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/courses/{courseId}/slides")
public class TtsController {

    private final TtsService ttsService;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    public TtsController(TtsService ttsService, CourseRepository courseRepository, EnrollmentRepository enrollmentRepository) {
        this.ttsService = ttsService;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    @PostMapping("/pages/{pageNumber}/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<SlidePageVO> generate(@PathVariable Long courseId,
                                    @PathVariable Integer pageNumber) {
        return R.ok(ttsService.generate(courseId, pageNumber));
    }

    @PostMapping("/audio/generate")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<Void> generateAll(@PathVariable Long courseId) {
        ttsService.generateAll(courseId);
        return R.ok();
    }

    @GetMapping("/pages/{pageNumber}/audio")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getAudio(@PathVariable Long courseId,
                                            @PathVariable Integer pageNumber) {
        verifyAccess(courseId);
        byte[] audioBytes = ttsService.getAudio(courseId, pageNumber);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CACHE_CONTROL,
                        CacheControl.maxAge(1, TimeUnit.HOURS).getHeaderValue())
                .body(audioBytes);
    }

    private void verifyAccess(Long courseId) {
        if (SecurityUtil.isAdmin() || SecurityUtil.hasRole("ACADEMIC")) return;
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        boolean allowed = false;
        if (SecurityUtil.hasRole("TEACHER")
                && course.getTeacherId() != null
                && course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
            allowed = true;
        }
        if (!allowed && SecurityUtil.hasRole("STUDENT")) {
            long count = enrollmentRepository.selectCount(
                    new LambdaQueryWrapper<Enrollment>()
                            .eq(Enrollment::getUserId, SecurityUtil.getCurrentUserId())
                            .eq(Enrollment::getCourseId, courseId)
                            .ne(Enrollment::getEnrollmentStatus, EnrollmentStatus.CANCELLED.getValue()));
            if (count > 0) allowed = true;
        }
        if (!allowed) throw new BusinessException(ErrorCode.NO_PERMISSION);
    }
}

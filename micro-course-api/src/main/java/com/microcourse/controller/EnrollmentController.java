package com.microcourse.controller;

import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentUpdateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.R;
import com.microcourse.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<EnrollmentVO> enroll(@Valid @RequestBody EnrollmentCreateRequest request) {
        EnrollmentVO vo = enrollmentService.enroll(request);
        return R.ok(vo);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<EnrollmentVO>> getMyEnrollments() {
        Long userId = getCurrentUserId();
        List<EnrollmentVO> list = enrollmentService.getMyEnrollments(userId);
        return R.ok(list);
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<List<EnrollmentVO>> getCourseEnrollments(@PathVariable Long courseId) {
        List<EnrollmentVO> list = enrollmentService.getCourseEnrollments(courseId);
        return R.ok(list);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public R<EnrollmentVO> updateEnrollment(@PathVariable Long id,
                                            @Valid @RequestBody EnrollmentUpdateRequest request) {
        EnrollmentVO vo = enrollmentService.updateEnrollment(id, request);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<Void> cancelEnrollment(@PathVariable Long id) {
        enrollmentService.cancelEnrollment(id);
        return R.ok();
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return null;
    }
}
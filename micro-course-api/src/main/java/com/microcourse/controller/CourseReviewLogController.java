package com.microcourse.controller;

import com.microcourse.dto.CourseReviewLogVO;
import com.microcourse.dto.R;
import com.microcourse.service.CourseReviewLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/course-review-logs")
public class CourseReviewLogController {

    private final CourseReviewLogService service;

    public CourseReviewLogController(CourseReviewLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC','TEACHER')")
    public R<List<CourseReviewLogVO>> listByCourse(@RequestParam Long courseId) {
        return R.ok(service.listByCourse(courseId));
    }
}

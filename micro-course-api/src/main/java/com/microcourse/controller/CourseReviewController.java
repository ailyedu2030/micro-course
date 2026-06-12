package com.microcourse.controller;

import com.microcourse.dto.CourseReviewCreateRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程评价控制器
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/courses/{courseId}/reviews")
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    public CourseReviewController(CourseReviewService courseReviewService) {
        this.courseReviewService = courseReviewService;
    }

    /**
     * 创建课程评价
     * POST /api/courses/{courseId}/reviews
     * 权限: STUDENT 或 ADMIN
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<CourseReviewVO> create(@PathVariable Long courseId,
                                    @Valid @RequestBody CourseReviewCreateRequest request) {
        Long userId = getCurrentUserId();
        CourseReviewVO vo = courseReviewService.create(request, userId);
        return R.ok(vo);
    }

    /**
     * 分页查询课程评价列表
     * GET /api/courses/{courseId}/reviews
     * 权限: 已认证用户
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseReviewVO>> list(@PathVariable Long courseId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(courseReviewService.listByCourse(courseId, page, size));
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return null;
    }
}

/**
 * 个人评价控制器（独立路径，不受 /api/courses/{courseId}/reviews 限制）
 */
@RestController
@RequestMapping("/api/reviews")
class MyReviewController {

    private final CourseReviewService courseReviewService;

    public MyReviewController(CourseReviewService courseReviewService) {
        this.courseReviewService = courseReviewService;
    }

    /**
     * 获取当前用户的所有评价
     * GET /api/reviews/my
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseReviewVO>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Long
                ? (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : 0L;
        PageResult<CourseReviewVO> result = courseReviewService.getMyReviews(userId, page, size);
        return R.ok(result);
    }
}
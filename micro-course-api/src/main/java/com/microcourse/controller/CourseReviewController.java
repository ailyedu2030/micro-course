package com.microcourse.controller;

import com.microcourse.dto.CourseReviewRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.CourseReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
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
@RequestMapping("/api/courses/{id}/reviews")
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    public CourseReviewController(CourseReviewService courseReviewService) {
        this.courseReviewService = courseReviewService;
    }

    /**
     * 创建课程评价
     * POST /api/courses/{id}/reviews
     * 权限: STUDENT 或 ADMIN
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public R<CourseReviewVO> create(@PathVariable Long id,
                                    @Valid @RequestBody CourseReviewRequest request) {
        Long userId = getCurrentUserId();
        CourseReviewVO vo = courseReviewService.create(id, request, userId);
        return R.ok(vo);
    }

    /**
     * 分页查询课程评价列表
     * GET /api/courses/{id}/reviews
     * 权限: 已认证用户
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseReviewVO>> list(@PathVariable Long id,
                                              @RequestParam(defaultValue = "0") @PositiveOrZero int page,
                                              @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size) {
        return R.ok(courseReviewService.listByCourse(id, page, size));
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        if (principal instanceof String) {
            try { return Long.parseLong((String) principal); } catch (NumberFormatException e) { /* fall through */ }
        }
        throw new BusinessException(ErrorCode.NO_PERMISSION);
    }
}

/**
 * 个人评价控制器（独立路径，不受 /api/courses/{id}/reviews 限制）
 */
@RestController
@RequestMapping("/api/reviews")
class MyReviewController {

    private final CourseReviewService courseReviewService;

    public MyReviewController(CourseReviewService courseReviewService) {
        this.courseReviewService = courseReviewService;
    }

    /**
     * 管理后台：分页查询所有评价
     * GET /api/reviews
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<PageResult<CourseReviewVO>> listAll(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size,
            @RequestParam(required = false) Long courseId) {
        PageResult<CourseReviewVO> result = courseReviewService.listAll(page, size, courseId);
        return R.ok(result);
    }

    /**
     * 获取当前用户的所有评价
     * GET /api/reviews/my
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<CourseReviewVO>> getMyReviews(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 200) int size) {
        Long userId = SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Long
                ? (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : 0L;
        PageResult<CourseReviewVO> result = courseReviewService.getMyReviews(userId, page, size);
        return R.ok(result);
    }

    /**
     * 删除评价
     * DELETE /api/reviews/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> deleteReview(@PathVariable Long id) {
        courseReviewService.deleteReview(id);
        return R.ok();
    }

    /**
     * 审核通过（当前无status字段，直接返回成功）
     * PUT /api/reviews/{id}/approve
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> approveReview(@PathVariable Long id) {
        return R.ok();
    }

    /**
     * 审核驳回 = 删除评价
     * PUT /api/reviews/{id}/reject
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> rejectReview(@PathVariable Long id) {
        courseReviewService.deleteReview(id);
        return R.ok();
    }
}
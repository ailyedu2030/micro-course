package com.microcourse.controller;

import com.microcourse.dto.CourseReviewRequest;
import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseReviewService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        Long userId = SecurityUtil.getCurrentUserId();
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
                                              @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        return R.ok(courseReviewService.listByCourse(id, page, size));
    }

    /**
     * 审核通过评价
     * POST /api/courses/{courseId}/reviews/{reviewId}/approve
     * 权限: ADMIN/ACADEMIC
     */
    @PostMapping("/{reviewId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> approve(@PathVariable Long id,
                           @PathVariable Long reviewId) {
        courseReviewService.approveReview(reviewId);
        return R.ok();
    }

    /**
     * 审核驳回评价（逻辑驳回，不物理删除）
     * POST /api/courses/{courseId}/reviews/{reviewId}/reject
     * 权限: ADMIN/ACADEMIC
     */
    @PostMapping("/{reviewId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> reject(@PathVariable Long id,
                          @PathVariable Long reviewId) {
        courseReviewService.rejectReview(reviewId);
        return R.ok();
    }

    /**
     * 删除评价
     * DELETE /api/courses/{courseId}/reviews/{reviewId}
     * 权限: ADMIN/ACADEMIC
     */
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> delete(@PathVariable Long id,
                          @PathVariable Long reviewId) {
        courseReviewService.deleteReview(reviewId);
        return R.ok();
    }

}

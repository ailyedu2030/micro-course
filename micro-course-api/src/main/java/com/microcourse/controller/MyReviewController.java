package com.microcourse.controller;

import com.microcourse.dto.CourseReviewVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseReviewService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 个人评价控制器（独立路径，不受 /api/courses/{id}/reviews 限制）
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@RestController
@RequestMapping("/api/reviews")
public class MyReviewController {

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
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size,
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
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        Long userId = SecurityUtil.getCurrentUserId();
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
     * 审核通过
     * PUT /api/reviews/{id}/approve
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> approveReview(@PathVariable Long id) {
        courseReviewService.approveReview(id);
        return R.ok();
    }

    /**
     * 审核驳回（逻辑驳回，不物理删除）
     * PUT /api/reviews/{id}/reject
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','ACADEMIC')")
    public R<Void> rejectReview(@PathVariable Long id) {
        courseReviewService.rejectReview(id);
        return R.ok();
    }

    /**
     * E4: 查询评价的回复列表
     * GET /api/reviews/{id}/replies
     */
    @GetMapping("/{id}/replies")
    @PreAuthorize("isAuthenticated()")
    public R<List<CourseReviewVO>> listReplies(@PathVariable Long id) {
        return R.ok(courseReviewService.listReplies(id));
    }
}

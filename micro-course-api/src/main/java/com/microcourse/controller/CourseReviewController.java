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

}

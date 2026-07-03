package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.TeacherRatingVO;
import com.microcourse.dto.TeacherTierLogVO;
import com.microcourse.service.TeacherRatingService;
import com.microcourse.util.SecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师评级管理
 */
@RestController
@RequestMapping("/api/teacher-ratings")
public class TeacherRatingController {

    private final TeacherRatingService ratingService;

    public TeacherRatingController(TeacherRatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * 教师自查评级
     * GET /api/teacher-ratings/my
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<TeacherRatingVO> getMyRating() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(ratingService.getMyRating(userId));
    }

    /**
     * 管理员查询所有教师评级
     * GET /api/teacher-ratings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public R<List<TeacherRatingVO>> listAll() {
        return R.ok(ratingService.listAllRatings());
    }

    /**
     * 按等级筛选
     * GET /api/teacher-ratings?tier=GOLD
     */
    @GetMapping("/by-tier")
    @PreAuthorize("hasRole('ADMIN')")
    public R<List<TeacherRatingVO>> listByTier(@RequestParam String tier) {
        return R.ok(ratingService.listByTier(tier.toUpperCase()));
    }

    /**
     * 教师查看自己的等级变更历史
     * GET /api/teacher-ratings/my/history
     */
    @GetMapping("/my/history")
    @PreAuthorize("isAuthenticated()")
    public R<List<TeacherTierLogVO>> getMyTierHistory() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(ratingService.getTierHistory(userId));
    }

    /**
     * 管理员手动调整教师等级
     * PUT /api/teacher-ratings/{teacherId}/tier
     */
    @PutMapping("/{teacherId}/tier")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Void> adjustTier(@PathVariable Long teacherId,
                              @RequestParam String newTier,
                              @RequestParam(required = false) String reason) {
        Long operatorId = SecurityUtil.getCurrentUserId();
        ratingService.adjustTier(teacherId, newTier.toUpperCase(), reason, operatorId);
        return R.ok(null);
    }

    /**
     * 手动重新计算指定教师评级
     * POST /api/teacher-ratings/{teacherId}/recalculate
     */
    @PostMapping("/{teacherId}/recalculate")
    @PreAuthorize("hasRole('ADMIN')")
    public R<TeacherRatingVO> recalculate(@PathVariable Long teacherId) {
        return R.ok(ratingService.recalculate(teacherId));
    }

    /**
     * P1-I 修复: 全部教师重新评级(批量端点,替代前端串行循环)
     * POST /api/teacher-ratings/recalculate-all
     */
    @PostMapping("/recalculate-all")
    @PreAuthorize("hasRole('ADMIN')")
    public R<Integer> recalculateAll() {
        return R.ok(ratingService.recalculateAll());
    }
}

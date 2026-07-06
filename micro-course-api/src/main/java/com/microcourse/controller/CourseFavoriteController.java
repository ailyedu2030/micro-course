package com.microcourse.controller;

import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.FavoriteCreateRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseFavoriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CourseFavoriteController {

    private final CourseFavoriteService favoriteService;

    public CourseFavoriteController(CourseFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * POST /api/courses/{id}/favorite
     * 收藏课程 — 权限矩阵 v4.0: 仅 STUDENT
     */
    @PostMapping("/courses/{id}/favorite")
    @PreAuthorize("hasRole('STUDENT')")
    public R<Map<String, Object>> favoriteCourse(@PathVariable("id") Long courseId) {
        Long userId = getCurrentUserId();
        Map<String, Object> result = favoriteService.favorite(userId, courseId);
        return R.ok(result);
    }

    /**
     * DELETE /api/courses/{id}/favorite
     * 取消收藏课程 — 权限矩阵 v4.0: 仅 STUDENT
     */
    @DeleteMapping("/courses/{id}/favorite")
    @PreAuthorize("hasRole('STUDENT')")
    public R<Void> unfavoriteCourse(@PathVariable("id") Long courseId) {
        Long userId = getCurrentUserId();
        favoriteService.unfavorite(userId, courseId);
        return R.ok();
    }

    /**
     * GET /api/courses/favorites/my
     * 我的收藏列表 — 权限矩阵 v4.0: STUDENT
     */
    @GetMapping("/courses/favorites/my")
    @PreAuthorize("hasRole('STUDENT')")
    public R<List<CourseFavoriteVO>> getMyFavorites() {
        Long userId = getCurrentUserId();
        List<CourseFavoriteVO> favorites = favoriteService.getMyFavorites(userId);
        return R.ok(favorites);
    }

    // ==================== 旧路径 (向后兼容) ====================

    /**
     * 【已废弃】POST /api/favorites — 兼容旧前端, 委托到 favoriteCourse
     */
    @Deprecated
    @PostMapping("/favorites")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> favorite(@Valid @RequestBody FavoriteCreateRequest request) {
        return favoriteCourse(request.getCourseId());
    }

    /**
     * 【已废弃】DELETE /api/favorites/{id} — 兼容旧前端 (id = courseId)
     */
    @Deprecated
    @DeleteMapping("/favorites/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> unfavorite(@PathVariable Long id) {
        return unfavoriteCourse(id);
    }

    /**
     * 【已废弃】GET /api/favorites/my
     */
    @Deprecated
    @GetMapping("/favorites/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<CourseFavoriteVO>> getMyFavoritesOld() {
        return getMyFavorites();
    }

    /**
     * GET /api/favorites — 分页查询所有收藏记录
     * 权限：ADMIN, ACADEMIC, TEACHER
     */
    @GetMapping("/favorites")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC', 'TEACHER')")
    public R<PageResult<CourseFavoriteVO>> listAll(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String courseName) {
        PageResult<CourseFavoriteVO> result = favoriteService.listAll(page, size, studentName, courseName);
        return R.ok(result);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        throw new com.microcourse.exception.BusinessException(com.microcourse.exception.ErrorCode.TOKEN_INVALID);
    }
}
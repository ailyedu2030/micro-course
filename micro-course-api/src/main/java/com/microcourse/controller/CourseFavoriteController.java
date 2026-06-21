package com.microcourse.controller;

import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.CourseFavoriteService;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class CourseFavoriteController {

    private final CourseFavoriteService favoriteService;

    public CourseFavoriteController(CourseFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> favorite(@RequestBody java.util.Map<String, Long> body) {
        Long courseId = body.get("courseId");
        Long userId = getCurrentUserId();
        favoriteService.favorite(userId, courseId);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> unfavorite(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        favoriteService.unfavorite(userId, id);
        return R.ok();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<CourseFavoriteVO>> getMyFavorites() {
        Long userId = getCurrentUserId();
        List<CourseFavoriteVO> favorites = favoriteService.getMyFavorites(userId);
        return R.ok(favorites);
    }

    /**
     * GET /api/favorites
     * 分页查询所有收藏记录
     * 权限：ADMIN, ACADEMIC
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ACADEMIC', 'TEACHER')")
    public R<PageResult<CourseFavoriteVO>> listAll(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 10000) int size,
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
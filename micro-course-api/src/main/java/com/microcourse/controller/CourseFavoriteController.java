package com.microcourse.controller;

import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.R;
import com.microcourse.service.CourseFavoriteService;
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
    public R<Void> favorite(@RequestParam Long courseId) {
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

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}
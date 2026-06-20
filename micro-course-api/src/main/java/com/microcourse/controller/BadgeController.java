package com.microcourse.controller;

import com.microcourse.dto.AchievementVO;
import com.microcourse.dto.BadgeDefinitionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.service.BadgeService;
import com.microcourse.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResult<BadgeDefinitionVO>> getDefinitions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(badgeService.getDefinitionsPage(page, size));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AchievementVO>> getMyBadges() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(badgeService.getMyAchievements(userId));
    }

    @GetMapping("/achievements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageResult<AchievementVO>> getMyAchievements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(badgeService.getMyAchievementsPage(userId, page, size));
    }
}

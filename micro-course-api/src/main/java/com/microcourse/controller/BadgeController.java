package com.microcourse.controller;

import com.microcourse.dto.AchievementVO;
import com.microcourse.dto.BadgeDefinitionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.R;
import com.microcourse.service.BadgeService;
import com.microcourse.util.SecurityUtil;
import jakarta.validation.constraints.PositiveOrZero;
import org.hibernate.validator.constraints.Range;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
@Validated
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<BadgeDefinitionVO>> getDefinitions(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        return R.ok(badgeService.getDefinitionsPage(page, size));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<AchievementVO>> getMyBadges() {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(badgeService.getMyAchievements(userId));
    }

    @GetMapping("/achievements")
    @PreAuthorize("isAuthenticated()")
    public R<PageResult<AchievementVO>> getMyAchievements(
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Range(min = 1, max = 100) int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        return R.ok(badgeService.getMyAchievementsPage(userId, page, size));
    }
}

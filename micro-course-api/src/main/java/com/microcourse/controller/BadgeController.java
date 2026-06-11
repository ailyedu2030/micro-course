package com.microcourse.controller;

import com.microcourse.dto.BadgeVO;
import com.microcourse.dto.R;
import com.microcourse.service.BadgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<BadgeVO>> getMyBadges() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        badgeService.checkAndGrantBadges(userId);
        List<BadgeVO> badges = badgeService.getMyBadges(userId);
        return R.ok(badges);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }
}

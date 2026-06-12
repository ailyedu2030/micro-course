package com.microcourse.controller;

import com.microcourse.dto.AchievementVO;
import com.microcourse.dto.BadgeDefinitionVO;
import com.microcourse.dto.R;
import com.microcourse.entity.BadgeDefinition;
import com.microcourse.repository.BadgeDefinitionRepository;
import com.microcourse.service.BadgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService badgeService;
    private final BadgeDefinitionRepository badgeDefinitionRepository;

    public BadgeController(BadgeService badgeService, BadgeDefinitionRepository badgeDefinitionRepository) {
        this.badgeService = badgeService;
        this.badgeDefinitionRepository = badgeDefinitionRepository;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<AchievementVO>> getMyBadges() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        badgeService.checkAndGrantBadges(userId);
        List<AchievementVO> badges = badgeService.getMyBadges(userId);
        return R.ok(badges);
    }

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    public R<List<BadgeDefinitionVO>> getBadgeDefinitions() {
        List<BadgeDefinition> definitions = badgeDefinitionRepository.selectAll();
        List<BadgeDefinitionVO> vos = definitions.stream()
                .map(this::convertToDefinitionVO)
                .collect(Collectors.toList());
        return R.ok(vos);
    }

    @GetMapping("/achievements")
    @PreAuthorize("isAuthenticated()")
    public R<List<AchievementVO>> getAchievements() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        badgeService.checkAndGrantBadges(userId);
        List<AchievementVO> achievements = badgeService.getMyBadges(userId);
        return R.ok(achievements);
    }

    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return null;
    }

    private BadgeDefinitionVO convertToDefinitionVO(BadgeDefinition definition) {
        BadgeDefinitionVO vo = new BadgeDefinitionVO();
        vo.setId(definition.getId());
        vo.setCode(definition.getCode());
        vo.setName(definition.getName());
        vo.setDescription(definition.getDescription());
        vo.setIconUrl(definition.getIconUrl());
        vo.setCategory(definition.getCategory());
        vo.setCriteria(definition.getCriteria());
        vo.setCreatedAt(definition.getCreatedAt());
        return vo;
    }
}

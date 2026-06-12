package com.microcourse.service;

import com.microcourse.dto.AchievementVO;

import java.util.List;

public interface BadgeService {

    List<AchievementVO> getMyBadges(Long userId);

    void checkAndGrantBadges(Long userId);

    void grantBadge(Long userId, String badgeCode);
}

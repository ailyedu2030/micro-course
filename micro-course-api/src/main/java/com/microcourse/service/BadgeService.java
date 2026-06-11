package com.microcourse.service;

import com.microcourse.dto.BadgeVO;

import java.util.List;

public interface BadgeService {

    List<BadgeVO> getMyBadges(Long userId);

    void checkAndGrantBadges(Long userId);

    void grantBadge(Long userId, String badgeType, String badgeName);
}

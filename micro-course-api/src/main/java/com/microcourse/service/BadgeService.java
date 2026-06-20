package com.microcourse.service;

import com.microcourse.dto.AchievementVO;
import com.microcourse.dto.BadgeDefinitionVO;
import com.microcourse.dto.PageResult;

import java.util.List;

public interface BadgeService {

    List<BadgeDefinitionVO> getAllDefinitions();

    PageResult<BadgeDefinitionVO> getDefinitionsPage(int page, int size);

    List<AchievementVO> getMyAchievements(Long userId);

    PageResult<AchievementVO> getMyAchievementsPage(Long userId, int page, int size);

    AchievementVO awardBadge(Long userId, String badgeCode);

    boolean hasBadge(Long userId, String badgeCode);

    void checkAndAwardCourseCompletion(Long userId, Long courseId, long totalEnrollments, long completedCount);

    void checkAndAwardStreak(Long userId, int consecutiveDays);
}

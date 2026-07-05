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

    // P1I-027: 以下为待实现的徽章检查方法（占位，后续补充）
    default void checkAndAwardVideoCount(Long userId, long totalVideos) {
        // TODO: 实现「观影达人」徽章 logic
    }

    default void checkAndAwardExerciseCount(Long userId, long totalExercises) {
        // TODO: 实现「刷题达人」徽章 logic
    }

    default void checkAndAwardReviewCount(Long userId, long reviewCount) {
        // TODO: 实现「复习达人」徽章 logic
    }

    default void checkAndAwardDiscussionCount(Long userId, long discussionCount) {
        // TODO: 实现「讨论达人」徽章 logic
    }
}

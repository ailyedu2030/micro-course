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

    // P1I-028: 新增 3 种徽章的颁发逻辑（对应 DB 中 6 种 badge_definitions 的剩余 3 种）

    /**
     * 检查并颁发「坚持不懈」徽章（THIRTY_DAY_STREAK）— 连续学习打卡 30 天。
     * 由学习打卡定时任务或打卡回调触发。
     */
    void checkAndAwardThirtyDayStreak(Long userId, int consecutiveDays);

    /**
     * 检查并颁发「满分达人」徽章（PERFECT_SCORE）— 单次练习获得满分。
     * 由练习提交回调触发。
     */
    void checkAndAwardPerfectScore(Long userId, Long exerciseId);

    /**
     * 检查并颁发「学习先锋」徽章（QUICK_LEARNER）— 累计学习达到 50 小时。
     * 由学习时长上报回调触发。
     */
    void checkAndAwardQuickLearner(Long userId, long totalHours);
}

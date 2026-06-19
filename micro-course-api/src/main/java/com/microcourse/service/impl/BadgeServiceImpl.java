package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.AchievementVO;
import com.microcourse.entity.Achievement;
import com.microcourse.entity.BadgeDefinition;
import com.microcourse.entity.CheckIn;
import com.microcourse.entity.Enrollment;
import com.microcourse.repository.AchievementRepository;
import com.microcourse.repository.BadgeDefinitionRepository;
import com.microcourse.repository.CheckInRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.BadgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BadgeServiceImpl implements BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeServiceImpl.class);

    private final AchievementRepository achievementRepository;
    private final BadgeDefinitionRepository badgeDefinitionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CheckInRepository checkInRepository;

    public BadgeServiceImpl(AchievementRepository achievementRepository,
                            BadgeDefinitionRepository badgeDefinitionRepository,
                            EnrollmentRepository enrollmentRepository,
                            CheckInRepository checkInRepository) {
        this.achievementRepository = achievementRepository;
        this.badgeDefinitionRepository = badgeDefinitionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.checkInRepository = checkInRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementVO> getMyBadges(Long userId) {
        List<Achievement> achievements = achievementRepository.selectByUserId(userId);
        return achievements.stream()
                .map(this::convertToAchievementVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndGrantBadges(Long userId) {
        checkFirstCourseBadge(userId);
        checkAllCoursesBadge(userId);
        checkSevenDayStreakBadge(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void grantBadge(Long userId, String badgeCode) {
        BadgeDefinition definition = badgeDefinitionRepository.selectByCode(badgeCode);
        if (definition == null) {
            log.warn("[Badge] definition not found for code: {}", badgeCode);
            return;
        }
        if (achievementRepository.existsByUserIdAndBadgeCode(userId, badgeCode)) {
            return;
        }
        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setBadgeCode(badgeCode);
        achievement.setBadgeName(definition.getName());
        achievement.setEarnedAt(LocalDateTime.now());
        try {
            achievementRepository.insert(achievement);
        } catch (org.springframework.dao.DuplicateKeyException dupEx) {
            // CON-NEW-1 修复:并发授予同一徽章时,第二个 insert 抛唯一约束冲突,降级忽略(不污染整个事务)
            log.debug("[Badge] 并发授予徽章 userId={} code={},DB UNIQUE 兜底", userId, badgeCode);
        }
    }

    private void checkFirstCourseBadge(Long userId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getCompleted, true);
        long completedCount = enrollmentRepository.selectCount(wrapper);
        if (completedCount >= 1) {
            grantBadge(userId, "FIRST_COURSE");
        }
    }

    private void checkAllCoursesBadge(Long userId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getEnrollmentStatus, "ENROLLED");
        long totalEnrollments = enrollmentRepository.selectCount(wrapper);

        wrapper.eq(Enrollment::getCompleted, true);
        long completedCount = enrollmentRepository.selectCount(wrapper);

        if (totalEnrollments > 0 && completedCount >= totalEnrollments) {
            grantBadge(userId, "ALL_COURSES");
        }
    }

    private void checkSevenDayStreakBadge(Long userId) {
        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckIn::getUserId, userId)
                .ge(CheckIn::getStreakDays, 7);
        CheckIn record = checkInRepository.selectOne(wrapper);
        if (record != null) {
            grantBadge(userId, "SEVEN_DAY_STREAK");
        }
    }

    private AchievementVO convertToAchievementVO(Achievement achievement) {
        AchievementVO vo = new AchievementVO();
        vo.setId(achievement.getId());
        vo.setUserId(achievement.getUserId());
        vo.setBadgeCode(achievement.getBadgeCode());
        vo.setBadgeName(achievement.getBadgeName());
        vo.setEarnedAt(achievement.getEarnedAt());

        BadgeDefinition definition = badgeDefinitionRepository.selectByCode(achievement.getBadgeCode());
        if (definition != null) {
            vo.setIconUrl(definition.getIconUrl());
            vo.setCategory(definition.getCategory());
            vo.setDescription(definition.getDescription());
            vo.setCriteria(definition.getCriteria());
        }
        return vo;
    }
}

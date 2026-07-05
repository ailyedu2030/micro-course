package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.AchievementVO;
import com.microcourse.dto.BadgeDefinitionVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Achievement;
import com.microcourse.entity.BadgeDefinition;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.AchievementRepository;
import com.microcourse.repository.BadgeDefinitionRepository;
import com.microcourse.service.BadgeService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BadgeServiceImpl implements BadgeService {

    private final BadgeDefinitionRepository badgeDefinitionRepository;
    private final AchievementRepository achievementRepository;

    // E2-2: self-injection 解决内部调用绕过 @Transactional 代理问题
    // @Lazy 避免循环依赖，通过 AOP 代理确保 hasBadge()/awardBadge() 的事务正确传播
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private BadgeServiceImpl self;

    public BadgeServiceImpl(BadgeDefinitionRepository badgeDefinitionRepository,
                           AchievementRepository achievementRepository) {
        this.badgeDefinitionRepository = badgeDefinitionRepository;
        this.achievementRepository = achievementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDefinitionVO> getAllDefinitions() {
        List<BadgeDefinition> definitions = badgeDefinitionRepository.selectList(null);
        return definitions.stream()
                .map(this::convertDefinitionToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BadgeDefinitionVO> getDefinitionsPage(int page, int size) {
        Page<BadgeDefinition> pg = new Page<>(page + 1, size);
        IPage<BadgeDefinition> result = badgeDefinitionRepository.selectPage(pg, null);
        PageResult<BadgeDefinitionVO> pageResult = new PageResult<>();
        pageResult.setItems(result.getRecords().stream()
                .map(this::convertDefinitionToVO)
                .collect(Collectors.toList()));
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotalElements(result.getTotal());
        pageResult.setTotalPages(result.getPages());
        return pageResult;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchievementVO> getMyAchievements(Long userId) {
        LambdaQueryWrapper<Achievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Achievement::getUserId, userId)
                .orderByDesc(Achievement::getEarnedAt);
        return achievementRepository.selectList(wrapper).stream()
                .map(this::convertAchievementToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AchievementVO> getMyAchievementsPage(Long userId, int page, int size) {
        Page<Achievement> pg = new Page<>(page + 1, size);
        LambdaQueryWrapper<Achievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Achievement::getUserId, userId)
                .orderByDesc(Achievement::getEarnedAt);
        IPage<Achievement> result = achievementRepository.selectPage(pg, wrapper);
        PageResult<AchievementVO> pageResult = new PageResult<>();
        pageResult.setItems(result.getRecords().stream()
                .map(this::convertAchievementToVO)
                .collect(Collectors.toList()));
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setTotalElements(result.getTotal());
        pageResult.setTotalPages(result.getPages());
        return pageResult;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AchievementVO awardBadge(Long userId, String badgeCode) {
        LambdaQueryWrapper<Achievement> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Achievement::getUserId, userId)
                .eq(Achievement::getBadgeCode, badgeCode);
        Achievement existing = achievementRepository.selectOne(existingWrapper);
        if (existing != null) {
            return convertAchievementToVO(existing);
        }

        BadgeDefinition definition = badgeDefinitionRepository.selectOne(
                new LambdaQueryWrapper<BadgeDefinition>()
                        .eq(BadgeDefinition::getCode, badgeCode));
        if (definition == null) {
            throw new BusinessException(ErrorCode.BADGE_NOT_FOUND);
        }

        Achievement achievement = new Achievement();
        achievement.setUserId(userId);
        achievement.setBadgeCode(badgeCode);
        achievement.setBadgeName(definition.getName());
        achievement.setEarnedAt(LocalDateTime.now());
        try {
            achievementRepository.insert(achievement);
        } catch (DuplicateKeyException e) {
            Achievement dup = achievementRepository.selectOne(
                    new LambdaQueryWrapper<Achievement>()
                            .eq(Achievement::getUserId, userId)
                            .eq(Achievement::getBadgeCode, badgeCode));
            if (dup != null) {
                return convertAchievementToVO(dup);
            }
            throw e;
        }

        return convertAchievementToVO(achievement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasBadge(Long userId, String badgeCode) {
        LambdaQueryWrapper<Achievement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Achievement::getUserId, userId)
                .eq(Achievement::getBadgeCode, badgeCode);
        return achievementRepository.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAwardCourseCompletion(Long userId, Long courseId,
                                             long totalEnrollments, long completedCount) {
        // E2-2: 使用 self 代理调用，确保 @Transactional(readOnly) / @Transactional 生效
        if (!self.hasBadge(userId, "FIRST_COURSE") && completedCount >= 1) {
            self.awardBadge(userId, "FIRST_COURSE");
        }

        if (totalEnrollments > 0 && !self.hasBadge(userId, "ALL_COURSES")
                && completedCount >= totalEnrollments) {
            self.awardBadge(userId, "ALL_COURSES");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAwardStreak(Long userId, int consecutiveDays) {
        if (consecutiveDays >= 7 && !self.hasBadge(userId, "SEVEN_DAY_STREAK")) {
            self.awardBadge(userId, "SEVEN_DAY_STREAK");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAwardThirtyDayStreak(Long userId, int consecutiveDays) {
        if (consecutiveDays >= 30 && !self.hasBadge(userId, "THIRTY_DAY_STREAK")) {
            self.awardBadge(userId, "THIRTY_DAY_STREAK");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAwardPerfectScore(Long userId, Long exerciseId) {
        if (!self.hasBadge(userId, "PERFECT_SCORE")) {
            self.awardBadge(userId, "PERFECT_SCORE");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndAwardQuickLearner(Long userId, long totalHours) {
        if (totalHours >= 50 && !self.hasBadge(userId, "QUICK_LEARNER")) {
            self.awardBadge(userId, "QUICK_LEARNER");
        }
    }

    private BadgeDefinitionVO convertDefinitionToVO(BadgeDefinition def) {
        BadgeDefinitionVO vo = new BadgeDefinitionVO();
        vo.setId(def.getId());
        vo.setCode(def.getCode());
        vo.setName(def.getName());
        vo.setDescription(def.getDescription());
        vo.setIconUrl(def.getIconUrl());
        vo.setCategory(def.getCategory());
        vo.setCriteria(def.getCriteria());
        vo.setCreatedAt(def.getCreatedAt());
        return vo;
    }

    private AchievementVO convertAchievementToVO(Achievement ach) {
        AchievementVO vo = new AchievementVO();
        vo.setId(ach.getId());
        vo.setUserId(ach.getUserId());
        vo.setBadgeCode(ach.getBadgeCode());
        vo.setBadgeName(ach.getBadgeName());
        vo.setEarnedAt(ach.getEarnedAt());
        return vo;
    }
}

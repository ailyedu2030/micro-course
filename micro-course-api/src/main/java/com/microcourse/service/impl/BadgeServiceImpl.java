package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.BadgeVO;
import com.microcourse.entity.Badge;
import com.microcourse.entity.CheckIn;
import com.microcourse.entity.Enrollment;
import com.microcourse.repository.BadgeRepository;
import com.microcourse.repository.CheckInRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.service.BadgeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BadgeServiceImpl implements BadgeService {

    private final BadgeRepository badgeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CheckInRepository checkInRepository;

    public BadgeServiceImpl(BadgeRepository badgeRepository,
                            EnrollmentRepository enrollmentRepository,
                            CheckInRepository checkInRepository) {
        this.badgeRepository = badgeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.checkInRepository = checkInRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeVO> getMyBadges(Long userId) {
        LambdaQueryWrapper<Badge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Badge::getUserId, userId)
                .orderByAsc(Badge::getEarnedAt);
        List<Badge> badges = badgeRepository.selectList(wrapper);
        return badges.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void checkAndGrantBadges(Long userId) {
        checkFirstCourseBadge(userId);
        checkAllCoursesBadge(userId);
        checkSevenDayStreakBadge(userId);
    }

    @Override
    @Transactional
    public void grantBadge(Long userId, String badgeType, String badgeName) {
        LambdaQueryWrapper<Badge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Badge::getUserId, userId)
                .eq(Badge::getBadgeType, badgeType);
        Badge existing = badgeRepository.selectOne(wrapper);
        if (existing != null) {
            return;
        }
        Badge badge = new Badge();
        badge.setUserId(userId);
        badge.setBadgeType(badgeType);
        badge.setBadgeName(badgeName);
        badge.setEarnedAt(LocalDateTime.now());
        badge.setCreatedAt(LocalDateTime.now());
        badgeRepository.insert(badge);
    }

    private void checkFirstCourseBadge(Long userId) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Enrollment::getUserId, userId)
                .eq(Enrollment::getCompleted, true);
        long completedCount = enrollmentRepository.selectCount(wrapper);
        if (completedCount >= 1) {
            grantBadge(userId, "FIRST_COURSE", "初识课程");
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
            grantBadge(userId, "ALL_COURSES", "学满全部");
        }
    }

    private void checkSevenDayStreakBadge(Long userId) {
        LambdaQueryWrapper<CheckIn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CheckIn::getUserId, userId)
                .ge(CheckIn::getStreakDays, 7);
        CheckIn record = checkInRepository.selectOne(wrapper);
        if (record != null) {
            grantBadge(userId, "SEVEN_DAY_STREAK", "连续打卡");
        }
    }

    private BadgeVO convertToVO(Badge badge) {
        BadgeVO vo = new BadgeVO();
        vo.setId(badge.getId());
        vo.setUserId(badge.getUserId());
        vo.setBadgeType(badge.getBadgeType());
        vo.setBadgeName(badge.getBadgeName());
        vo.setEarnedAt(badge.getEarnedAt());
        return vo;
    }
}

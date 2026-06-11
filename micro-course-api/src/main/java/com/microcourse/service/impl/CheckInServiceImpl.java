package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CheckInVO;
import com.microcourse.entity.CheckIn;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CheckInRepository;
import com.microcourse.service.CheckInService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CheckInServiceImpl implements CheckInService {

    private final CheckInRepository checkInRepository;

    public CheckInServiceImpl(CheckInRepository checkInRepository) {
        this.checkInRepository = checkInRepository;
    }

    @Override
    @Transactional
    public CheckInVO checkIn(Long userId) {
        LocalDate today = LocalDate.now();

        // 幂等性优先检查：今日已打卡则直接返回
        CheckIn existing = checkInRepository.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckinDate, today)
        );
        if (existing != null) {
            return convertToVO(existing);
        }

        // 计算 streak_days：从昨日往前逐日检查连续天数
        LocalDate yesterday = today.minusDays(1);
        int streak = 0;
        LocalDate checkDate = yesterday;

        while (true) {
            CheckIn record = checkInRepository.selectOne(
                    new LambdaQueryWrapper<CheckIn>()
                            .eq(CheckIn::getUserId, userId)
                            .eq(CheckIn::getCheckinDate, checkDate)
            );
            if (record == null) {
                break;
            }
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        // 新增打卡记录
        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckinDate(today);
        checkIn.setDuration(0);
        checkIn.setStreakDays(streak + 1);
        checkIn.setCreatedAt(LocalDateTime.now());
        checkInRepository.insert(checkIn);

        return convertToVO(checkIn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInVO> getMyCheckIns(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days - 1);

        List<CheckIn> records = checkInRepository.selectList(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .ge(CheckIn::getCheckinDate, startDate)
                        .orderByDesc(CheckIn::getCheckinDate)
        );

        return records.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public int getStreak(Long userId) {
        LocalDate today = LocalDate.now();

        // 先查今日是否有打卡
        CheckIn todayRecord = checkInRepository.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckinDate, today)
        );

        if (todayRecord == null) {
            // 今日未打卡，从昨日往前检查
            return countStreakFromDate(userId, today.minusDays(1));
        }

        // 今日已打卡，从今日往前检查
        int streak = todayRecord.getStreakDays();
        return streak;
    }

    @Override
    @Transactional
    public void updateDuration(Long userId, int duration) {
        LocalDate today = LocalDate.now();

        CheckIn todayRecord = checkInRepository.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckinDate, today)
        );

        if (todayRecord != null) {
            todayRecord.setDuration(duration);
            checkInRepository.updateById(todayRecord);
        }
    }

    private int countStreakFromDate(Long userId, LocalDate startDate) {
        int streak = 0;
        LocalDate checkDate = startDate;

        while (true) {
            CheckIn record = checkInRepository.selectOne(
                    new LambdaQueryWrapper<CheckIn>()
                            .eq(CheckIn::getUserId, userId)
                            .eq(CheckIn::getCheckinDate, checkDate)
            );
            if (record == null) {
                break;
            }
            streak++;
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }

    private CheckInVO convertToVO(CheckIn checkIn) {
        CheckInVO vo = new CheckInVO();
        vo.setId(checkIn.getId());
        vo.setUserId(checkIn.getUserId());
        vo.setCheckinDate(checkIn.getCheckinDate());
        vo.setDuration(checkIn.getDuration());
        vo.setStreakDays(checkIn.getStreakDays());
        vo.setCreatedAt(checkIn.getCreatedAt());
        return vo;
    }
}
package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CheckInVO;
import com.microcourse.entity.CheckIn;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CheckInRepository;
import com.microcourse.service.BadgeService;
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CheckInServiceImpl.class);

    private final CheckInRepository checkInRepository;
    private final BadgeService badgeService;

    public CheckInServiceImpl(CheckInRepository checkInRepository, BadgeService badgeService) {
        this.checkInRepository = checkInRepository;
        this.badgeService = badgeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 计算 streak_days:在 @Transactional 内用 SERIALIZABLE 读取最近一条,避免跨天边界读到未提交值(CON-006 修复)
// 退化方案:SELECT ... FOR UPDATE 在 RR 隔离下可防止幻读,MyBatis-Plus 通过 wrapper.last() 附加
        LambdaQueryWrapper<CheckIn> lastWrapper = new LambdaQueryWrapper<>();
        lastWrapper.eq(CheckIn::getUserId, userId)
                .lt(CheckIn::getCheckinDate, today)
                .orderByDesc(CheckIn::getCheckinDate)
                .last("LIMIT 1 FOR UPDATE");
        CheckIn lastRecord;
        try {
            lastRecord = checkInRepository.selectOne(lastWrapper);
        } catch (Exception lockEx) {
            // 部分 PG 配置下 FOR UPDATE 在只读路径报错,降级为普通 SELECT
            log.debug("[CheckIn] FOR UPDATE 失败,降级为普通 SELECT userId={}", userId, lockEx);
            LambdaQueryWrapper<CheckIn> fallback = new LambdaQueryWrapper<>();
            fallback.eq(CheckIn::getUserId, userId)
                    .lt(CheckIn::getCheckinDate, today)
                    .orderByDesc(CheckIn::getCheckinDate)
                    .last("LIMIT 1");
            lastRecord = checkInRepository.selectOne(fallback);
        }

        int streak = 0;
        if (lastRecord != null) {
            LocalDate lastDate = lastRecord.getCheckinDate();
            // 检查是否连续:昨天有打卡则基于 streakDays+1,否则重新开始
            if (lastDate.equals(today.minusDays(1))) {
                streak = lastRecord.getStreakDays();
            }
        }

        // 新增打卡记录(DB UNIQUE(user_id,checkin_date) 兜底防并发双条)
        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setCheckinDate(today);
        checkIn.setDuration(0);
        checkIn.setStreakDays(streak + 1);
        checkIn.setCreatedAt(LocalDateTime.now());
        try {
            checkInRepository.insert(checkIn);
        } catch (DuplicateKeyException e) {
            // 并发时后到者走幂等回查
            CheckIn existingAfterRace = checkInRepository.selectOne(
                    new LambdaQueryWrapper<CheckIn>()
                            .eq(CheckIn::getUserId, userId)
                            .eq(CheckIn::getCheckinDate, today)
            );
            if (existingAfterRace != null) return convertToVO(existingAfterRace);
            throw e;
        }

        try {
            badgeService.checkAndAwardStreak(userId, streak + 1);
        } catch (Exception badgeEx) {
            log.warn("[CheckIn] 徽章颁发失败 userId={}", userId, badgeEx);
        }

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
    @Transactional(rollbackFor = Exception.class)
    public void updateDuration(Long userId, int duration) {
        LocalDate today = LocalDate.now();

        // P1-I: 改为先查询再更新，避免 setSql 字符串拼接参数
        CheckIn checkIn = checkInRepository.selectOne(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .eq(CheckIn::getCheckinDate, today));
        if (checkIn != null) {
            checkIn.setDuration((checkIn.getDuration() != null ? checkIn.getDuration() : 0) + duration);
            checkInRepository.updateById(checkIn);
        } else {
            log.warn("[CheckIn] updateDuration 无匹配 userId={} date={}, 可能是今日未打卡", userId, today);
        }
    }

    private int countStreakFromDate(Long userId, LocalDate startDate) {
        // 性能优化: 单次查询获取最近30天打卡记录，替代逐日N+1查询
        List<CheckIn> recent = checkInRepository.selectList(
                new LambdaQueryWrapper<CheckIn>()
                        .eq(CheckIn::getUserId, userId)
                        .ge(CheckIn::getCheckinDate, startDate.minusDays(30))
                        .orderByDesc(CheckIn::getCheckinDate));
        if (recent.isEmpty()) return 0;

        int streak = 0;
        LocalDate expected = startDate;
        for (CheckIn record : recent) {
            if (!record.getCheckinDate().equals(expected)) break;
            streak++;
            expected = expected.minusDays(1);
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
package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.dto.TeacherRatingVO;
import com.microcourse.dto.TeacherTierLogVO;
import com.microcourse.entity.TeacherRating;
import com.microcourse.entity.TeacherTierLog;
import com.microcourse.entity.User;
import com.microcourse.repository.TeacherRatingRepository;
import com.microcourse.repository.TeacherTierLogRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.PlatformShareRateResolver;
import com.microcourse.service.TeacherRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师评级服务实现
 *
 * <p>评级公式：
 * ratingScore = avgStudentRating * 20 * 0.4
 *             + completionRate * 0.3
 *             + enrollmentRate * 100 * 0.15
 *             + courseCountFactor * 100 * 0.15
 * <br>
 * 其中 enrollmentRate = min(totalStudents / 100, 1)
 *      courseCountFactor = min(totalCourses / 5, 1)
 *
 * <p>等级阈值：
 * NEW: 默认初始
 * BRONZE: 0-40
 * SILVER: 40-60
 * GOLD: 60-80
 * PLATINUM: 80-100
 */
@Service
public class TeacherRatingServiceImpl implements TeacherRatingService {

    private static final Logger log = LoggerFactory.getLogger(TeacherRatingServiceImpl.class);

    private final TeacherRatingRepository ratingRepository;
    private final TeacherTierLogRepository tierLogRepository;
    private final UserRepository userRepository;
    private final PlatformShareRateResolver rateResolver;

    // 等级阈值配置
    private static final BigDecimal BRONZE_THRESHOLD = new BigDecimal("0");
    private static final BigDecimal SILVER_THRESHOLD = new BigDecimal("40");
    private static final BigDecimal GOLD_THRESHOLD = new BigDecimal("60");
    private static final BigDecimal PLATINUM_THRESHOLD = new BigDecimal("80");

    // 标签映射
    private static final Map<String, String> TIER_LABELS = new HashMap<>();
    static {
        TIER_LABELS.put("NEW", "新教师");
        TIER_LABELS.put("BRONZE", "青铜");
        TIER_LABELS.put("SILVER", "白银");
        TIER_LABELS.put("GOLD", "黄金");
        TIER_LABELS.put("PLATINUM", "铂金");
    }

    public TeacherRatingServiceImpl(TeacherRatingRepository ratingRepository,
                                    TeacherTierLogRepository tierLogRepository,
                                    UserRepository userRepository,
                                    PlatformShareRateResolver rateResolver) {
        this.ratingRepository = ratingRepository;
        this.tierLogRepository = tierLogRepository;
        this.userRepository = userRepository;
        this.rateResolver = rateResolver;
    }

    @Override
    public TeacherRatingVO getMyRating(Long teacherId) {
        TeacherRating rating = ratingRepository.selectOne(
                new LambdaQueryWrapper<TeacherRating>()
                        .eq(TeacherRating::getTeacherId, teacherId));
        if (rating == null) {
            return createDefaultVO(teacherId);
        }
        return toVO(rating);
    }

    @Override
    public List<TeacherRatingVO> listAllRatings() {
        List<TeacherRating> ratings = ratingRepository.selectList(
                new LambdaQueryWrapper<TeacherRating>()
                        .orderByDesc(TeacherRating::getRatingScore));
        return toVOList(ratings);
    }

    @Override
    public List<TeacherRatingVO> listByTier(String tier) {
        List<TeacherRating> ratings = ratingRepository.selectList(
                new LambdaQueryWrapper<TeacherRating>()
                        .eq(TeacherRating::getTier, tier)
                        .orderByDesc(TeacherRating::getRatingScore));
        return toVOList(ratings);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherRatingVO recalculate(Long teacherId) {
        // P1-2 修复: 用单教师 SQL,替代 selectTeacherStats()+遍历过滤
        TeacherRatingRepository.TeacherRatingStatRow target = ratingRepository.selectTeacherStat(teacherId);
        if (target == null) {
            log.warn("[TeacherRating] 未找到教师数据 teacherId={}", teacherId);
            return createDefaultVO(teacherId);
        }
        return calculateAndSave(target);
    }

    /**
     * 全部教师重新评级。
     * P2-5 修复: 移除 @Transactional,因为循环内 catch 了异常并继续处理,
     * 单个失败不应回滚所有已完成的写入(每个教师独立事务)
     */
    @Override
    public int recalculateAll() {
        List<TeacherRatingRepository.TeacherRatingStatRow> stats = ratingRepository.selectTeacherStats();
        int processed = 0;
        for (TeacherRatingRepository.TeacherRatingStatRow row : stats) {
            try {
                calculateAndSave(row);
                processed++;
            } catch (Exception e) {
                log.error("[TeacherRating] 评级计算失败 teacherId={}", row.getTeacherId(), e);
            }
        }
        log.info("[TeacherRating] 全部教师评级完成: total={}", processed);
        return processed;
    }

    /**
     * 修复 P1-4: 防止 cron 与手动重算竞态
     * 5 分钟内已计算过的教师跳过,避免重复写 tier_log
     */
    private static final long RECALCULATE_SKIP_MS = 5 * 60 * 1000L;

    private boolean shouldSkipRecentRecalc(TeacherRating existing) {
        if (existing == null || existing.getCalculatedAt() == null) return false;
        long elapsed = System.currentTimeMillis()
                - existing.getCalculatedAt().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
        return elapsed < RECALCULATE_SKIP_MS;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustTier(Long teacherId, String newTier, String reason, Long operatorId) {
        TeacherRating rating = ratingRepository.selectOne(
                new LambdaQueryWrapper<TeacherRating>()
                        .eq(TeacherRating::getTeacherId, teacherId));
        if (rating == null) {
            throw new IllegalArgumentException("教师评级记录不存在，请先执行评级计算");
        }
        String oldTier = rating.getTier();

        // 更新评级
        LambdaUpdateWrapper<TeacherRating> uw = new LambdaUpdateWrapper<TeacherRating>()
                .eq(TeacherRating::getTeacherId, teacherId)
                .set(TeacherRating::getTier, newTier)
                .set(TeacherRating::getUpdatedAt, LocalDateTime.now());
        ratingRepository.update(null, uw);

        // 记录日志
        TeacherTierLog logEntry = new TeacherTierLog();
        logEntry.setTeacherId(teacherId);
        logEntry.setFromTier(oldTier);
        logEntry.setToTier(newTier);
        logEntry.setReason(reason);
        logEntry.setTriggeredBy("ADMIN");
        logEntry.setOperatorId(operatorId);
        logEntry.setCreatedAt(LocalDateTime.now());
        tierLogRepository.insert(logEntry);

        log.info("[TeacherRating] 管理员调整等级 teacherId={}: {} → {}, reason={}", teacherId, oldTier, newTier, reason);
    }

    @Override
    public List<TeacherTierLogVO> getTierHistory(Long teacherId) {
        List<TeacherTierLog> logs = tierLogRepository.selectList(
                new LambdaQueryWrapper<TeacherTierLog>()
                        .eq(TeacherTierLog::getTeacherId, teacherId)
                        .orderByDesc(TeacherTierLog::getCreatedAt));
        List<TeacherTierLogVO> result = new ArrayList<>();
        for (TeacherTierLog log : logs) {
            TeacherTierLogVO vo = new TeacherTierLogVO();
            vo.setFromTier(log.getFromTier());
            vo.setFromTierLabel(TIER_LABELS.getOrDefault(log.getFromTier(), log.getFromTier()));
            vo.setToTier(log.getToTier());
            vo.setToTierLabel(TIER_LABELS.getOrDefault(log.getToTier(), log.getToTier()));
            vo.setReason(log.getReason());
            vo.setTriggeredBy(log.getTriggeredBy());
            vo.setCreatedAt(log.getCreatedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public String determineTier(BigDecimal score) {
        if (score == null) return "NEW";
        if (score.compareTo(PLATINUM_THRESHOLD) >= 0) return "PLATINUM";
        if (score.compareTo(GOLD_THRESHOLD) >= 0) return "GOLD";
        if (score.compareTo(SILVER_THRESHOLD) >= 0) return "SILVER";
        if (score.compareTo(BRONZE_THRESHOLD) >= 0) return "BRONZE";
        return "NEW";
    }

    // ========== 内部方法 ==========

    private TeacherRatingVO calculateAndSave(TeacherRatingRepository.TeacherRatingStatRow row) {
        Long teacherId = row.getTeacherId();
        int courseCount = row.getCourseCount();
        int studentCount = row.getStudentCount();
        double completionRate = row.getCompletionRate();
        double avgRating = row.getAvgRating();

        // 修复 P1-4: 5 分钟内已计算过则跳过,防止 cron+手动重算竞态
        TeacherRating preCheck = ratingRepository.selectOne(
                new LambdaQueryWrapper<TeacherRating>()
                        .eq(TeacherRating::getTeacherId, teacherId));
        if (shouldSkipRecentRecalc(preCheck)) {
            log.debug("[TeacherRating] 5 分钟内已重算,跳过 teacherId={}", teacherId);
            return toVO(preCheck);
        }

        // 计算评分
        // avgStudentRating 按 5 分制，转为 0-100 分
        double avgRatingScore = avgRating * 20.0; // 0-100
        double enrollmentRate = Math.min(studentCount / 100.0, 1.0);
        double courseCountFactor = Math.min(courseCount / 5.0, 1.0);

        double rawScore = avgRatingScore * 0.4
                + completionRate * 0.3
                + enrollmentRate * 100.0 * 0.15
                + courseCountFactor * 100.0 * 0.15;

        BigDecimal score = BigDecimal.valueOf(rawScore)
                .setScale(2, RoundingMode.HALF_UP);
        String tier = determineTier(score);
        BigDecimal bdAvgRating = BigDecimal.valueOf(avgRating).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bdCompletionRate = BigDecimal.valueOf(completionRate).setScale(2, RoundingMode.HALF_UP);

        // P2-4 修复: 复用 preCheck 作为 existing,消除重复 selectOne
        // preCheck 已在 calculateAndSave 开头查询,这里直接复用

        if (preCheck != null) {
            String oldTier = preCheck.getTier();
            if (!oldTier.equals(tier)) {
                TeacherTierLog logEntry = new TeacherTierLog();
                logEntry.setTeacherId(teacherId);
                logEntry.setFromTier(oldTier);
                logEntry.setToTier(tier);
                logEntry.setReason(String.format("评分 %.2f → 等级变更", score));
                logEntry.setTriggeredBy("CRON");
                logEntry.setCreatedAt(LocalDateTime.now());
                tierLogRepository.insert(logEntry);
                log.info("[TeacherRating] 等级变更 teacherId={}: {} → {} (score={})", teacherId, oldTier, tier, score);
            }
        } else if (!"NEW".equals(tier)) {
            // 首次评级且非 NEW
            TeacherTierLog logEntry = new TeacherTierLog();
            logEntry.setTeacherId(teacherId);
            logEntry.setFromTier("NEW");
            logEntry.setToTier(tier);
            logEntry.setReason(String.format("首次评级: %.2f", score));
            logEntry.setTriggeredBy("CRON");
            logEntry.setCreatedAt(LocalDateTime.now());
            tierLogRepository.insert(logEntry);
        }

        // INSERT ON CONFLICT 完成写入/更新
        ratingRepository.upsertRating(teacherId, score, tier,
                bdAvgRating, bdCompletionRate, studentCount, courseCount);

        // P2-4 修复: 直接构造 VO,不再额外 selectOne
        TeacherRating mockRating = new TeacherRating();
        mockRating.setTeacherId(teacherId);
        mockRating.setRatingScore(score);
        mockRating.setTier(tier);
        mockRating.setAvgStudentRating(bdAvgRating);
        mockRating.setCompletionRate(bdCompletionRate);
        mockRating.setTotalStudents(studentCount);
        mockRating.setTotalCourses(courseCount);
        mockRating.setCalculatedAt(LocalDateTime.now());
        return toVO(mockRating);
    }

    /**
     * 单条转 VO - 每次都查 user。P1-1 修：批量场景用 toVOList
     */
    private TeacherRatingVO toVO(TeacherRating rating) {
        return toVOWithUser(rating, null);
    }

    /**
     * 批量转 VO - userMap 预加载,避免 N+1 (P1-1 修复)
     */
    private List<TeacherRatingVO> toVOList(List<TeacherRating> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return new ArrayList<>();
        }
        // 批量预加载所有教师
        List<Long> teacherIds = ratings.stream()
                .map(TeacherRating::getTeacherId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        Map<Long, User> userMap = userRepository.selectBatchIds(teacherIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        List<TeacherRatingVO> result = new ArrayList<>(ratings.size());
        for (TeacherRating r : ratings) {
            result.add(toVOWithUser(r, userMap.get(r.getTeacherId())));
        }
        return result;
    }

    private TeacherRatingVO toVOWithUser(TeacherRating rating, User preloadedUser) {
        TeacherRatingVO vo = new TeacherRatingVO();
        vo.setTeacherId(rating.getTeacherId());
        vo.setRatingScore(rating.getRatingScore());
        vo.setTier(rating.getTier());
        vo.setTierLabel(TIER_LABELS.getOrDefault(rating.getTier(), rating.getTier()));
        vo.setAvgStudentRating(rating.getAvgStudentRating());
        vo.setCompletionRate(rating.getCompletionRate());
        vo.setTotalStudents(rating.getTotalStudents());
        vo.setTotalCourses(rating.getTotalCourses());
        vo.setCalculatedAt(rating.getCalculatedAt());

        // 修复 P0-2: 用 PlatformShareRateResolver 读 config 表的真实分账率
        // 之前硬编码导致 GOLD 22% vs 25% 不一致
        BigDecimal tierRate = rateResolver.getRateByTier(rating.getTier());
        vo.setTierRate(tierRate);
        vo.setTeacherRate(new BigDecimal("100").subtract(tierRate));

        // 填充教师姓名(优先用预加载的 user)
        User teacher = preloadedUser != null ? preloadedUser : userRepository.selectById(rating.getTeacherId());
        if (teacher != null) {
            vo.setTeacherName(teacher.getRealName());
            vo.setTeacherAvatar(teacher.getAvatar());
        }
        return vo;
    }

    private TeacherRatingVO createDefaultVO(Long teacherId) {
        TeacherRatingVO vo = new TeacherRatingVO();
        vo.setTeacherId(teacherId);
        vo.setRatingScore(BigDecimal.ZERO);
        vo.setTier("NEW");
        vo.setTierLabel("新教师");
        vo.setAvgStudentRating(BigDecimal.ZERO);
        vo.setCompletionRate(BigDecimal.ZERO);
        vo.setTotalStudents(0);
        vo.setTotalCourses(0);

        User teacher = userRepository.selectById(teacherId);
        if (teacher != null) {
            vo.setTeacherName(teacher.getRealName());
            vo.setTeacherAvatar(teacher.getAvatar());
        }
        return vo;
    }
}

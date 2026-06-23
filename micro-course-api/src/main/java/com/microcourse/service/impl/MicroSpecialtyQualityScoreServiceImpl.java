package com.microcourse.service.impl;

import com.microcourse.entity.MicroSpecialty;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.MicroSpecialtyQualityScoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 微专业质量分服务实现（Phase 14 — G1 修复）
 *
 * <p>核心算法：
 * <ol>
 *   <li>enrollmentRate = min(studentCount / maxStudents, 1)
 *       — 数据源：micro_specialties.student_count / max_students
 *       — maxStudents=0（不限）时按 1.0 处理</li>
 *   <li>completionRate = COUNT(COMPLETED) / MAX(COUNT(IN_PROGRESS+COMPLETED), 1)
 *       — 数据源：enrollments 表按 course_id 分组</li>
 *   <li>avgRating = AVG(course_reviews.rating) WHERE course IN (ms courses)
 *       — 数据源：course_reviews 表</li>
 * </ol>
 *
 * <p>缓存：Redis 1 小时，key 格式 {@code msq:score:{id}}，value 为字符串数字
 *
 * @author Phase14-Development-Team
 * @since 2026-06-23
 */
@Service
public class MicroSpecialtyQualityScoreServiceImpl implements MicroSpecialtyQualityScoreService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyQualityScoreServiceImpl.class);

    private static final String CACHE_KEY_PREFIX = "msq:score:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    /** 抽象缓存接口（方便测试 mock；生产实现走 StringRedisTemplate） */
    public interface Cache {
        String get(String key);
        void set(String key, String value, Duration ttl);
        void delete(String key);
    }

    /** StringRedisTemplate 适配器 */
    public static class RedisStringCache implements Cache {
        private final StringRedisTemplate template;
        public RedisStringCache(StringRedisTemplate template) { this.template = template; }
        @Override public String get(String key) {
            try { return template.opsForValue().get(key); } catch (Exception e) { return null; }
        }
        @Override public void set(String key, String value, Duration ttl) {
            try { template.opsForValue().set(key, value, ttl); } catch (Exception ignored) {}
        }
        @Override public void delete(String key) {
            try { template.delete(key); } catch (Exception ignored) {}
        }
    }

    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final Cache cache;

    /** Spring 生产环境：通过 Cache 接口注入（RedisStringCache 由配置类提供） */
    public MicroSpecialtyQualityScoreServiceImpl(MicroSpecialtyRepository msRepository,
                                                  MicroSpecialtyCourseRepository msCourseRepository,
                                                  EnrollmentRepository enrollmentRepository,
                                                  CourseReviewRepository courseReviewRepository,
                                                  Cache cache) {
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseReviewRepository = courseReviewRepository;
        this.cache = cache;
    }

    @Override
    public BigDecimal calculate(Long microSpecialtyId) {
        if (microSpecialtyId == null) return BigDecimal.ZERO;
        String key = CACHE_KEY_PREFIX + microSpecialtyId;
        String cached = cache.get(key);
        if (cached != null) {
            return new BigDecimal(cached);
        }
        BigDecimal score = computeFromDb(Collections.singletonList(microSpecialtyId))
                .getOrDefault(microSpecialtyId, BigDecimal.ZERO);
        cache.set(key, score.toPlainString(), CACHE_TTL);
        return score;
    }

    @Override
    public Map<Long, BigDecimal> calculateBatch(List<Long> microSpecialtyIds) {
        if (microSpecialtyIds == null || microSpecialtyIds.isEmpty()) return Collections.emptyMap();

        Map<Long, BigDecimal> result = new HashMap<>();
        List<Long> missIds = new ArrayList<>();

        // 1. 先查 Redis 缓存
        for (Long id : microSpecialtyIds) {
            String cached = cache.get(CACHE_KEY_PREFIX + id);
            if (cached != null) {
                result.put(id, new BigDecimal(cached));
            } else {
                missIds.add(id);
            }
        }

        // 2. 缓存未命中的走 DB 批量计算
        if (!missIds.isEmpty()) {
            Map<Long, BigDecimal> dbScores = computeFromDb(missIds);
            for (Map.Entry<Long, BigDecimal> entry : dbScores.entrySet()) {
                result.put(entry.getKey(), entry.getValue());
                cache.set(CACHE_KEY_PREFIX + entry.getKey(),
                          entry.getValue().toPlainString(), CACHE_TTL);
            }
        }
        return result;
    }

    /**
     * 从 DB 批量计算质量分（核心算法）
     */
    private Map<Long, BigDecimal> computeFromDb(List<Long> msIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (msIds.isEmpty()) return result;

        // 1. 拉主表数据（student_count, max_students）
        List<MicroSpecialty> msList = msRepository.selectBatchIds(msIds);
        Map<Long, MicroSpecialty> msMap = msList.stream()
                .collect(Collectors.toMap(MicroSpecialty::getId, m -> m, (a, b) -> a));

        // 2. 拉所有课程 ID
        List<Map<String, Object>> courseLinks = msCourseRepository.selectCourseIdsByMsIds(msIds);
        Map<Long, List<Long>> msCourseMap = new HashMap<>();
        Set<Long> allCourseIds = new java.util.HashSet<>();
        for (Map<String, Object> link : courseLinks) {
            Long msId = toLong(link.get("micro_specialty_id"));
            Long courseId = toLong(link.get("course_id"));
            if (msId == null || courseId == null) continue;
            msCourseMap.computeIfAbsent(msId, k -> new ArrayList<>()).add(courseId);
            allCourseIds.add(courseId);
        }

        // 3. 批量拉完成数 / 进行中+完成数
        Map<Long, Long> completedMap = batchCountToMap(
                allCourseIds.isEmpty() ? Collections.emptyList() : new ArrayList<>(allCourseIds),
                enrollmentRepository::countCompletedByCourseIds);

        Map<Long, Long> inProgressOrCompletedMap = batchCountToMap(
                allCourseIds.isEmpty() ? Collections.emptyList() : new ArrayList<>(allCourseIds),
                enrollmentRepository::countInProgressOrCompletedByCourseIds);

        // 4. 批量拉平均评分
        Map<Long, BigDecimal> ratingMap = new HashMap<>();
        if (!allCourseIds.isEmpty()) {
            List<Map<String, Object>> ratingRows = courseReviewRepository.avgRatingByCourseIds(new ArrayList<>(allCourseIds));
            for (Map<String, Object> row : ratingRows) {
                Long courseId = toLong(row.get("course_id"));
                Object avgVal = row.get("avg_rating");
                BigDecimal avg = avgVal == null ? BigDecimal.ZERO :
                        (avgVal instanceof BigDecimal ? (BigDecimal) avgVal : new BigDecimal(avgVal.toString()));
                ratingMap.put(courseId, avg);
            }
        }

        // 5. 逐个 MS 计算
        for (Long msId : msIds) {
            MicroSpecialty ms = msMap.get(msId);
            if (ms == null) {
                result.put(msId, BigDecimal.ZERO);
                continue;
            }

            // enrollmentRate
            int studentCount = ms.getStudentCount() == null ? 0 : ms.getStudentCount();
            int maxStudents = ms.getMaxStudents() == null ? 0 : ms.getMaxStudents();
            BigDecimal enrollmentRate = (maxStudents <= 0)
                    ? BigDecimal.ONE
                    : BigDecimal.valueOf(studentCount).divide(BigDecimal.valueOf(maxStudents), 4, RoundingMode.HALF_UP);
            if (enrollmentRate.compareTo(BigDecimal.ONE) > 0) enrollmentRate = BigDecimal.ONE;

            // completionRate / avgRating
            List<Long> courseIds = msCourseMap.getOrDefault(msId, Collections.emptyList());

            // Global ratio: SUM(completed) / MAX(SUM(total), 1)
            long totalCompleted = 0;
            long totalInProgressOrCompleted = 0;
            for (Long courseId : courseIds) {
                totalCompleted += completedMap.getOrDefault(courseId, 0L);
                totalInProgressOrCompleted += inProgressOrCompletedMap.getOrDefault(courseId, 0L);
            }
            BigDecimal completionRate = totalInProgressOrCompleted > 0
                    ? BigDecimal.valueOf((double) totalCompleted / totalInProgressOrCompleted)
                            .setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal avgRatingSum = BigDecimal.ZERO;
            int ratingCount = 0;
            for (Long courseId : courseIds) {
                BigDecimal rating = ratingMap.get(courseId);
                if (rating != null && rating.compareTo(BigDecimal.ZERO) > 0) {
                    avgRatingSum = avgRatingSum.add(rating);
                    ratingCount++;
                }
            }
            BigDecimal avgRating = ratingCount > 0
                    ? avgRatingSum.divide(BigDecimal.valueOf(ratingCount), 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // qualityScore = enrollmentRate × 0.5 + completionRate × 0.3 + (avgRating/5) × 0.2
            BigDecimal score = enrollmentRate.multiply(new BigDecimal("0.5"))
                    .add(completionRate.multiply(new BigDecimal("0.3")))
                    .add(avgRating.divide(new BigDecimal("5"), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("0.2")))
                    .setScale(4, RoundingMode.HALF_UP);

            result.put(msId, score);
        }
        return result;
    }

    /**
     * 把 List<Map> 转 Map<courseId, count>
     */
    private Map<Long, Long> batchCountToMap(List<Long> courseIds,
                                              java.util.function.Function<List<Long>, List<Map<String, Object>>> queryFn) {
        Map<Long, Long> map = new HashMap<>();
        if (courseIds.isEmpty()) return map;
        try {
            List<Map<String, Object>> rows = queryFn.apply(courseIds);
            for (Map<String, Object> row : rows) {
                Long courseId = toLong(row.get("course_id"));
                Long cnt = toLong(row.get("cnt"));
                if (courseId != null) map.put(courseId, cnt == null ? 0L : cnt);
            }
        } catch (Exception e) {
            log.warn("[QualityScore] batch count failed: {}", e.getMessage());
        }
        return map;
    }

    @Override
    public void evictCache(Long microSpecialtyId) {
        if (microSpecialtyId == null) return;
        cache.delete(CACHE_KEY_PREFIX + microSpecialtyId);
    }

    @Override
    public void evictAllCache() {
        // 注：批量删除留给上层（Service 写完数据时调 evictCache 即可）
        // 此方法保留为接口契约，但生产场景建议用 evictCache 逐个清
        log.info("[QualityScore] evictAllCache called - no-op in current impl");
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Long) return (Long) o;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(Objects.toString(o)); } catch (Exception e) { return null; }
    }
}

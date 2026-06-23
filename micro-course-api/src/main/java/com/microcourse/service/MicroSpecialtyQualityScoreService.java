package com.microcourse.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 微专业质量分服务（Phase 14 — G1 修复）
 *
 * <p>质量分公式（spec §6.8）：
 * <pre>
 * quality_score = enrollmentRate × 0.5 + completionRate × 0.3 + (avgRating/5) × 0.2
 * enrollmentRate = min(studentCount / maxStudents, 1)
 * completionRate = COUNT(COMPLETED) / MAX(COUNT(IN_PROGRESS+COMPLETED), 1)
 * avgRating = AVG(course_reviews.rating WHERE course IN (ms courses))
 * </pre>
 *
 * <p>缓存：Redis 1 小时（避免重复计算热门微专业）
 *
 * @author Phase14-Development-Team
 * @since 2026-06-23
 */
public interface MicroSpecialtyQualityScoreService {

    /**
     * 计算单个微专业的质量分（带 1 小时 Redis 缓存）
     *
     * @param microSpecialtyId 微专业 ID
     * @return 质量分（0-1 之间，4 位小数）
     */
    BigDecimal calculate(Long microSpecialtyId);

    /**
     * 批量计算多个微专业的质量分（带 1 小时 Redis 缓存，N+1 优化）
     *
     * @param microSpecialtyIds 微专业 ID 列表
     * @return Map<微专业ID, 质量分>
     */
    Map<Long, BigDecimal> calculateBatch(List<Long> microSpecialtyIds);

    /**
     * 清除指定微专业的质量分缓存（数据变更时调用）
     */
    void evictCache(Long microSpecialtyId);

    /**
     * 清除所有质量分缓存
     */
    void evictAllCache();
}

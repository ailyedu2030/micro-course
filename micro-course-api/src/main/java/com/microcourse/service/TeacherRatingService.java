package com.microcourse.service;

import com.microcourse.dto.TeacherRatingVO;

import java.util.List;

/**
 * 教师评级服务
 */
public interface TeacherRatingService {

    /**
     * 获取教师评级（自查）
     */
    TeacherRatingVO getMyRating(Long teacherId);

    /**
     * 获取所有教师评级列表（管理员）
     */
    List<TeacherRatingVO> listAllRatings();

    /**
     * 按等级筛选教师（管理员）
     */
    List<TeacherRatingVO> listByTier(String tier);

    /**
     * 手动重新计算指定教师的评级
     */
    TeacherRatingVO recalculate(Long teacherId);

    /**
     * 全部教师重新评级（定时任务调用）
     * @return 处理的教师数
     */
    int recalculateAll();

    /**
     * 手动调整教师等级（管理员）
     */
    void adjustTier(Long teacherId, String newTier, String reason, Long operatorId);

    /**
     * 根据评分确定等级
     */
    String determineTier(java.math.BigDecimal score);

    /** 获取教师等级变更历史 */
    java.util.List<com.microcourse.dto.TeacherTierLogVO> getTierHistory(Long teacherId);
}

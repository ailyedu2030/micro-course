package com.microcourse.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 课程评价 Mapper
 *
 * @author Phase9-Development-Team
 * @since 2026-06-12
 */
@Mapper
public interface CourseReviewRepository extends BaseMapper<CourseReview> {

    /**
     * Phase 11: 批量查询课程评价数量（避免 N+1）
     * 返回 List of Map，格式: [{course_id: Long, cnt: Long}, ...]
     */
    @Select("<script>" +
            "SELECT course_id, COUNT(*) as cnt FROM course_reviews " +
            "WHERE deleted_at IS NULL AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY course_id" +
            "</script>")
    List<Map<String, Object>> countByCourseIds(List<Long> courseIds);

    /**
     * 根据课程ID统计评价数量
     */
    default long countByCourseId(Long courseId) {
        return selectCount(new LambdaQueryWrapper<CourseReview>()
                .eq(CourseReview::getCourseId, courseId)
                .isNull(CourseReview::getDeletedAt));
    }

    /**
     * 计算课程的平均评分（仅限未删除评价）
     */
    @Select("SELECT COALESCE(AVG(rating), 0) FROM course_reviews WHERE course_id = #{courseId} AND deleted_at IS NULL")
    BigDecimal selectAvgRatingByCourseId(Long courseId);

    /**
     * Phase 14: 批量查询多门课程的平均评分（单条聚合 SQL）
     * 返回 List of Map, 格式: [{course_id: Long, avg_rating: BigDecimal}, ...]
     */
    @Select("<script>" +
            "SELECT course_id, COALESCE(AVG(rating), 0) AS avg_rating FROM course_reviews " +
            "WHERE deleted_at IS NULL AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY course_id" +
            "</script>")
    List<Map<String, Object>> avgRatingByCourseIds(@Param("courseIds") List<Long> courseIds);
}

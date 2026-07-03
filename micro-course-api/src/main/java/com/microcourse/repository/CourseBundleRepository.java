package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseBundle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CourseBundleRepository extends BaseMapper<CourseBundle> {

    /**
     * 原子减少套餐学习人数（退款时调用）。
     * GREATEST 兜底避免出现负数；COALESCE 兜底 NULL。
     */
    @Update("UPDATE course_bundles SET student_count = GREATEST(COALESCE(student_count, 0) - 1, 0), " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{bundleId} AND deleted_at IS NULL")
    int atomicDecrementStudentCount(@Param("bundleId") Long bundleId);

    /**
     * 首次购买原子递增：仅当该用户无 PAID 订单时 +1，否则 no-op。
     * 使用单条 SQL 在数据库层面保证并发安全，避免 check-then-act 竞态。
     */
    @Update("UPDATE course_bundles SET student_count = COALESCE(student_count, 0) + 1, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{bundleId} AND deleted_at IS NULL " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM orders " +
            "    WHERE orders.bundle_id = #{bundleId} " +
            "      AND orders.user_id = #{userId} " +
            "      AND orders.status = 'PAID' " +
            "  )")
    int atomicIncrementIfFirstTime(@Param("bundleId") Long bundleId, @Param("userId") Long userId);
}

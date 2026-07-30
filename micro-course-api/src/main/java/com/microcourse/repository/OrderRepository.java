package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderRepository extends BaseMapper<Order> {

    /**
     * 查找指定用户对指定课程的 PAID 订单（单课程场景）。
     */
    @Select("SELECT * FROM orders WHERE user_id = #{userId} AND course_id = #{courseId} "
            + "AND status = 'PAID' "
            + "ORDER BY paid_at DESC LIMIT 1")
    Order findPaidOrder(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * P0-ORPHAN-001 修复：硬删除指定用户的所有订单（含 payments CASCADE）。
     * UserRetentionCleanupJob 在物理删除用户前必须先调此方法，
     * 否则 orders_user_id_fkey 的 RESTRICT 约束会阻塞 users 物理删除。
     *
     * <p>FK 级联链：payments.order_id → orders.id ON DELETE CASCADE，
     * 所以删 orders 会自动删 payments；本方法返回被删 orders 数量（用于日志）。
     * @return 受影响 orders 行数
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM orders WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);

    /**
     * 查找指定用户对指定课程的所有 PAID 订单（含套餐场景）。
     */
    @Select("SELECT * FROM orders WHERE user_id = #{userId} "
            + "AND (course_id = #{courseId} OR bundle_id IN "
            + "(SELECT bundle_id FROM course_bundle_items WHERE course_id = #{courseId})) "
            + "AND status = 'PAID' "
            + "ORDER BY paid_at DESC")
    List<Order> findPaidOrdersByCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}

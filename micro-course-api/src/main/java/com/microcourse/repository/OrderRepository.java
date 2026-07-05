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
     * 查找指定用户对指定课程的所有 PAID 订单（含套餐场景）。
     */
    @Select("SELECT * FROM orders WHERE user_id = #{userId} "
            + "AND (course_id = #{courseId} OR bundle_id IN "
            + "(SELECT bundle_id FROM course_bundle_items WHERE course_id = #{courseId})) "
            + "AND status = 'PAID' "
            + "ORDER BY paid_at DESC")
    List<Order> findPaidOrdersByCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}

package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CartItemRepository extends BaseMapper<CartItem> {

    @Select("SELECT * FROM cart_items WHERE user_id = #{userId} AND deleted_at IS NULL ORDER BY updated_at DESC")
    List<CartItem> findActiveByUserId(@Param("userId") Long userId);
}

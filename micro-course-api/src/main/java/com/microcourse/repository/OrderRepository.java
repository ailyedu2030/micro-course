package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Order;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderRepository extends BaseMapper<Order> {
}

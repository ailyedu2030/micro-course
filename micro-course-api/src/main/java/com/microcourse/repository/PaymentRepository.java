package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRepository extends BaseMapper<Payment> {
}

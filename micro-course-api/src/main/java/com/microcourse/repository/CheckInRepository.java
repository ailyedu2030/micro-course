package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CheckIn;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CheckInRepository extends BaseMapper<CheckIn> {
}
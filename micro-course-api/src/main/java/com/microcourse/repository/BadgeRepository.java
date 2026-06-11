package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Badge;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BadgeRepository extends BaseMapper<Badge> {
}

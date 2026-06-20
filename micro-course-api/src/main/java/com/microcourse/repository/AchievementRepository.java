package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Achievement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AchievementRepository extends BaseMapper<Achievement> {
}

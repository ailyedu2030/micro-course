package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.LearningProgress;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LearningProgressRepository extends BaseMapper<LearningProgress> {
}
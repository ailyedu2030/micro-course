package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ScoreHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScoreHistoryRepository extends BaseMapper<ScoreHistory> {
}

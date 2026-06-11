package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ExerciseRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExerciseRecordRepository extends BaseMapper<ExerciseRecord> {
}
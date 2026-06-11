package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ExerciseQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExerciseQuestionRepository extends BaseMapper<ExerciseQuestion> {
}
package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WrongQuestionRepository extends BaseMapper<WrongQuestion> {
}
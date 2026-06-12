package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Grade;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GradeRepository extends BaseMapper<Grade> {
}
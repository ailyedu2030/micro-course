package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.GradeComponent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GradeComponentRepository extends BaseMapper<GradeComponent> {
}

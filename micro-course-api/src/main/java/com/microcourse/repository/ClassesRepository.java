package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Classes;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClassesRepository extends BaseMapper<Classes> {
}

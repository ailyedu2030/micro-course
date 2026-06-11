package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Major;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MajorRepository extends BaseMapper<Major> {
}
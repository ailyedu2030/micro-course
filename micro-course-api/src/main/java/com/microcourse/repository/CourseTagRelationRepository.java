package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseTagRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseTagRelationRepository extends BaseMapper<CourseTagRelation> {
}
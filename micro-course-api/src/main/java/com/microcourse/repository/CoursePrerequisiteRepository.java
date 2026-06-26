package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CoursePrerequisite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoursePrerequisiteRepository extends BaseMapper<CoursePrerequisite> {
}

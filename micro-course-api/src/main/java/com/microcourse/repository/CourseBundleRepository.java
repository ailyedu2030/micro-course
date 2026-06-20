package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseBundle;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseBundleRepository extends BaseMapper<CourseBundle> {
}

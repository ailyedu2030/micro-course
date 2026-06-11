package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseCategory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseCategoryRepository extends BaseMapper<CourseCategory> {
}
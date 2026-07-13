package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseSection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseSectionRepository extends BaseMapper<CourseSection> {
}

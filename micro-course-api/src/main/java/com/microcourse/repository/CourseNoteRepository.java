package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseNote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CourseNoteRepository extends BaseMapper<CourseNote> {
}

package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Lesson;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LessonRepository extends BaseMapper<Lesson> {
}

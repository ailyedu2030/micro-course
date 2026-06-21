package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.QuestionChapter;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionChapterRepository extends BaseMapper<QuestionChapter> {
}

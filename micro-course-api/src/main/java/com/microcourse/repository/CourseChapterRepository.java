package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseChapterRepository extends BaseMapper<CourseChapter> {
    /**
     * 忽略@TableLogic软删除,查询course_id下的最大sort_order
     */
    @Select("SELECT COALESCE(MAX(sort_order), 0) FROM course_chapters WHERE course_id = #{courseId}")
    int selectMaxSortOrderByCourseId(Long courseId);
}
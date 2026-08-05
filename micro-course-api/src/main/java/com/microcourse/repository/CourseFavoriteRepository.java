package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.CourseFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CourseFavoriteRepository extends BaseMapper<CourseFavorite> {

    /**
     * 恢复已软删除的收藏记录（uk_cf_user_course 唯一约束包含软删行，
     * 重新收藏时必须恢复原行而非插入新行，否则唯一键冲突 409）。
     *
     * @return 恢复成功的行数（0 = 无软删记录，需走 insert）
     */
    @Update("UPDATE course_favorites SET deleted_at = NULL " +
            "WHERE user_id = #{userId} AND course_id = #{courseId} AND deleted_at IS NOT NULL")
    int restoreByUserAndCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}

package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.TeachingClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeachingClassStudentRepository extends BaseMapper<TeachingClassStudent> {

    @Select("SELECT * FROM teaching_class_students WHERE class_id = #{classId}")
    List<TeachingClassStudent> selectByClassId(@Param("classId") Long classId);

    @Select("SELECT * FROM teaching_class_students WHERE class_id = #{classId} AND status = 'APPROVED'")
    List<TeachingClassStudent> selectActiveByClassId(@Param("classId") Long classId);

    @Select("SELECT COUNT(*) > 0 FROM teaching_class_students WHERE class_id = #{classId} AND user_id = #{userId}")
    boolean existsByClassIdAndUserId(@Param("classId") Long classId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM teaching_class_students WHERE class_id = #{classId} AND status = 'ENROLLED'")
    int countActiveByClassId(@Param("classId") Long classId);
}
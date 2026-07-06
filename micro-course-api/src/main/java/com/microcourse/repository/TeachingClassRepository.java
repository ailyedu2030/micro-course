package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.TeachingClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeachingClassRepository extends BaseMapper<TeachingClass> {

    /**
     * 【根因】P2-8: addStudent 容量检查 TOCTOU 竞态 — student_count >= max_students 检查与原子递增之间无 DB 级约束
     * 【修复】SELECT ... FOR UPDATE 行级锁，确保容量检查在事务中独占
     * 【防止再发】所有涉及容量/计数检查的写操作必须加行锁
     */
    @Select("SELECT * FROM teaching_classes WHERE id = #{classId} FOR UPDATE")
    TeachingClass selectByIdForUpdate(@Param("classId") Long classId);
}

package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EnrollmentRepository extends BaseMapper<Enrollment> {

    /**
     * ★ Round 11-2 性能优化：教师维度选课平均分（单条聚合 SQL）。
     * 替代「先查教师课程 ID 集合 + 全量加载选课记录 + 内存求均值」的全表扫描式实现。
     * 语义等价：仅统计该教师<b>未删除课程</b>下、{@code final_score} 非空且<b>未删除</b>的选课记录均分；
     * 无匹配记录时 AVG 返回 NULL，由调用方兜底为 0。raw SQL 不经 @TableLogic，故 deleted_at 过滤显式书写。
     */
    @Select("SELECT AVG(e.final_score) FROM enrollments e "
            + "JOIN courses c ON e.course_id = c.id "
            + "WHERE c.teacher_id = #{teacherId} "
            + "  AND e.final_score IS NOT NULL "
            + "  AND e.deleted_at IS NULL "
            + "  AND c.deleted_at IS NULL")
    Double avgScoreByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * ★ Round 11-2 性能优化：单门课程选课平均分（单条聚合 SQL）。
     * 替代 computeStats 中「全量加载该课程已评分选课记录 + 内存求均值」。
     * 语义等价：统计该课程下 {@code final_score} 非空且<b>未删除</b>的选课记录均分；无匹配返回 NULL。
     */
    @Select("SELECT AVG(final_score) FROM enrollments "
            + "WHERE course_id = #{courseId} "
            + "  AND final_score IS NOT NULL "
            + "  AND deleted_at IS NULL")
    Double avgScoreByCourseId(@Param("courseId") Long courseId);
}

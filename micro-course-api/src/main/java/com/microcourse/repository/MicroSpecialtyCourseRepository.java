package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.MicroSpecialtyCourse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MicroSpecialtyCourseRepository extends BaseMapper<MicroSpecialtyCourse> {

    /**
     * Phase 14: 批量查询多个微专业的课程 ID 列表
     * 返回 List of Map, 格式: [{micro_specialty_id: Long, course_id: Long}, ...]
     */
    @Select("<script>" +
            "SELECT micro_specialty_id, course_id FROM micro_specialty_courses " +
            "WHERE micro_specialty_id IN " +
            "<foreach collection='msIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<java.util.Map<String, Object>> selectCourseIdsByMsIds(@Param("msIds") List<Long> msIds);

    /**
     * Phase 14: 查询指定微专业下所有课程 ID
     */
    default List<Long> selectCourseIdsByMsId(Long msId) {
        return selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId))
                .stream().map(MicroSpecialtyCourse::getCourseId).collect(java.util.stream.Collectors.toList());
    }
}

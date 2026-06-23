package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.MicroSpecialty;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MicroSpecialtyRepository extends BaseMapper<MicroSpecialty> {

    /** FOR UPDATE 锁定行，防止 classImport 并发 */
    @Select("SELECT * FROM micro_specialties WHERE id = #{id} FOR UPDATE")
    MicroSpecialty selectForUpdate(@Param("id") Long id);
}

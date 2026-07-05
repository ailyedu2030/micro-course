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

    /**
     * P0-S02: 获取排他 advisory lock，保护金标设置"全校 ≤ 2"约束的原子性。
     * pg_advisory_xact_lock 在事务提交/回滚时自动释放。
     */
    @Select("SELECT pg_advisory_xact_lock(42)")
    void acquireGoldFeaturedLock();
}

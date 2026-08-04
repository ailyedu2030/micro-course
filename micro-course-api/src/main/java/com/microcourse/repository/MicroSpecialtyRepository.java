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
    // P1-C 修复 (2026-08-04): pg_advisory_xact_lock 返回 void，JDBC/MyBatis 无法映射
    // （"No constructor found in void" / "Bad value for type int"）→ 金标设置 100% 失败。
    // 改用 pg_try_advisory_xact_lock（返回 boolean，事务结束自动释放），
    // Service 层检查返回值：false = 并发冲突，明确拒绝而非"系统繁忙"。
    @Select("SELECT pg_try_advisory_xact_lock(42)")
    boolean tryAcquireGoldFeaturedLock();
}

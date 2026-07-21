package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventDedup;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * V314 domain_event_dedup 表 MyBatis-Plus Repository.
 * 关键 SQL:
 *   - insertIgnoreDuplicate: INSERT ... ON CONFLICT DO NOTHING
 *     这是双向幂等的核心: 同一 event_id 二次到达, 不抛异常, 不重复副作用
 *   - existsByEventId: SELECT EXISTS, 用于 Hermes 推过来时 1 个 query 拒重
 *
 * P1 spec §三.3.2 + §五.5.4 幂等保证.
 */
@Mapper
public interface DomainEventDedupRepository extends BaseMapper<DomainEventDedup> {

    @Insert("""
        INSERT INTO domain_event_dedup (event_id, source, trace_id, processed_at, created_at)
        VALUES (#{eventId}, #{source}, #{traceId}, NOW(), NOW())
        ON CONFLICT (event_id) DO NOTHING
    """)
    int insertIgnoreDuplicate(DomainEventDedup row);

    @Select("SELECT EXISTS(SELECT 1 FROM domain_event_dedup WHERE event_id = #{eventId})")
    boolean existsByEventId(@Param("eventId") String eventId);
}

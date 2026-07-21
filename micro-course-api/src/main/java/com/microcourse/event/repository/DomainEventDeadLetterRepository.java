package com.microcourse.event.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.event.DomainEventDeadLetter;
import org.apache.ibatis.annotations.Mapper;

/**
 * V315 domain_event_dead_letter 表 MyBatis-Plus Repository.
 * BaseMapper 已足够: 简单 insert + selectById + updateById.
 *
 * 业务逻辑 (countUnacked / acknowledge / forceRessurect) 由 DeadLetterService 接管.
 * P1 spec §三.3.3 + §五.5.2.
 */
@Mapper
public interface DomainEventDeadLetterRepository extends BaseMapper<DomainEventDeadLetter> {
}

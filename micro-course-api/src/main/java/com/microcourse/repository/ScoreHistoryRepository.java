package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ScoreHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * @deprecated 当前 sprint 暂无计划实现成绩变更审计追踪，
 *             留待 Phase E (Q2-2026) 处理。表/实体/Repository 已建好，
 *             待后续 PR 加 ScoreHistoryService.recordChange() 时直接调用。
 */
@Deprecated
@Mapper
public interface ScoreHistoryRepository extends BaseMapper<ScoreHistory> {
}

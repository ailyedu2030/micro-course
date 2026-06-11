package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.LearningProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearningProgressRepository extends BaseMapper<LearningProgress> {

    /**
     * SQL聚合查询总观看时长，避免全表加载到内存（OOM修复）
     */
    @Select("SELECT COALESCE(SUM(total_watch_time), 0) FROM learning_progress WHERE deleted_at IS NULL")
    Long sumTotalWatchTime();
}
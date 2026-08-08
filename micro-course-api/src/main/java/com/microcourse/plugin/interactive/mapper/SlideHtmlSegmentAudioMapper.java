package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * slide_html_segment_audios mapper (V305).
 * audio_token 部分索引, 流式 GET endpoint 用.
 *
 * 【BUG #9 修复】 加 listByScriptIds 批量查询, 消除 HTML buildHtmlTree N+1.
 * 【Q-1 修复】 加原子抢占 (claimPending/selectClaimed/releaseClaim/reclaimOrphans/markTimedOut),
 *            TtsWorker 多节点/多线程 scheduler 下不重复合成同一行 (worker_id 标识, V330).
 * 【U-5 修复】 播放音色确定性: ORDER BY is_default DESC, completed_at DESC (V330 is_default 列).
 */
@Mapper
public interface SlideHtmlSegmentAudioMapper extends BaseMapper<SlideHtmlSegmentAudio> {

    /**
     * U-5: 单 segment script 音频列表 — 默认音色优先, 其次最新完成 (播放器确定性).
     */
    @Select("SELECT * FROM slide_html_segment_audios WHERE segment_script_id = #{scriptId} "
          + "ORDER BY is_default DESC, completed_at DESC")
    List<SlideHtmlSegmentAudio> listByScript(@Param("scriptId") Long scriptId);

    /**
     * Q-2 (N+1 修复): 批量取多个 segment script 的所有音频 (1 SQL 取代 N 次 listByScript).
     */
    @Select("<script>SELECT * FROM slide_html_segment_audios WHERE segment_script_id IN "
          + "<foreach collection='scriptIds' item='sid' open='(' separator=',' close=')'>"
          + "  #{sid}"
          + "</foreach> "
          + "ORDER BY is_default DESC, completed_at DESC</script>")
    List<SlideHtmlSegmentAudio> listByScriptIds(@Param("scriptIds") List<Long> scriptIds);

    @Select("SELECT * FROM slide_html_segment_audios WHERE audio_token = #{token} LIMIT 1")
    SlideHtmlSegmentAudio findByToken(@Param("token") String token);

    /**
     * 【BUG #9 修复】 批量取多个 unit 的所有音频 (1 SQL).
     */
    @Select("<script>SELECT * FROM slide_html_segment_audios WHERE segment_script_id IN "
          + "(SELECT id FROM slide_html_segment_scripts "
          + " WHERE is_active = TRUE "
          + "   AND html_unit_id IN "
          + "     <foreach collection='unitIds' item='uid' open='(' separator=',' close=')'>"
          + "     #{uid}"
          + "     </foreach>)</script>")
    List<SlideHtmlSegmentAudio> listByUnitIds(@Param("unitIds") List<Long> unitIds);

    // ====== Q-1: TtsWorker 幂等抢占 (V330 worker_id 列) ======

    /**
     * Q-1: 原子抢占待消费行. PostgreSQL UPDATE 行锁 + WHERE 重评估保证
     * 多 worker 并发 claim 不重复 (不重复扣 MiniMax API).
     */
    @Update("UPDATE slide_html_segment_audios SET status='PROCESSING', worker_id=#{workerId}, "
            + "generation_started_at=CURRENT_TIMESTAMP "
            + "WHERE id IN (SELECT id FROM slide_html_segment_audios "
            + "  WHERE status='GENERATING' AND (worker_id IS NULL OR worker_id='') "
            + "  AND generation_started_at IS NOT NULL AND generation_started_at < #{before} "
            + "  ORDER BY id LIMIT #{limit})")
    int claimPending(@Param("workerId") String workerId,
                     @Param("before") LocalDateTime before,
                     @Param("limit") int limit);

    /** Q-1: 取回本 worker 抢占到的行. */
    @Select("SELECT * FROM slide_html_segment_audios WHERE status='PROCESSING' AND worker_id = #{workerId} "
            + "ORDER BY id ASC")
    List<SlideHtmlSegmentAudio> selectClaimed(@Param("workerId") String workerId);

    /**
     * Q-1: 处理失败 → 回滚 status='GENERATING' (worker_id 清空), 下轮重试.
     */
    @Update("UPDATE slide_html_segment_audios SET status='GENERATING', worker_id=NULL "
            + "WHERE id = #{id} AND status='PROCESSING' AND worker_id = #{workerId}")
    int releaseClaim(@Param("id") Long id, @Param("workerId") String workerId);

    /**
     * Q-1: 回收崩溃 worker 遗留的 PROCESSING 行 → 恢复 GENERATING 供下轮重试.
     */
    @Update("UPDATE slide_html_segment_audios SET status='GENERATING', worker_id=NULL "
            + "WHERE status='PROCESSING' AND generation_started_at < #{before}")
    int reclaimOrphans(@Param("before") LocalDateTime before);

    /**
     * Q-1: 长时间未消费的 GENERATING 行 → FAILED (保留更具体的 error_message).
     */
    @Update("UPDATE slide_html_segment_audios SET status='FAILED', "
            + "error_message=COALESCE(error_message, '生成超时（>10 分钟）'), completed_at=CURRENT_TIMESTAMP "
            + "WHERE status='GENERATING' AND generation_started_at IS NOT NULL "
            + "AND generation_started_at < #{timeoutBefore}")
    int markTimedOut(@Param("timeoutBefore") LocalDateTime timeoutBefore);
}

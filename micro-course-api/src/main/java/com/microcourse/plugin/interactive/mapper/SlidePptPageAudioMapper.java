package com.microcourse.plugin.interactive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * slide_ppt_page_audios mapper (V302).
 * audio_token 是部分索引 (WHERE audio_token IS NOT NULL), 用于 7-19 P1-C 兼容的流式 GET.
 *
 * 【BUG #9 修复】 加 listByScriptIds 批量查询, 消除 CoursewareQueryService N+1.
 * 【Q-1 修复】 加原子抢占 (claimPending/selectClaimed/releaseClaim/reclaimOrphans/markTimedOut),
 *            TtsWorker 多节点/多线程 scheduler 下不重复合成同一行 (worker_id 标识, V330).
 * 【U-5 修复】 播放音色确定性: ORDER BY is_default DESC, completed_at DESC (V330 is_default 列).
 */
@Mapper
public interface SlidePptPageAudioMapper extends BaseMapper<SlidePptPageAudio> {

    /**
     * U-5: 单 script 音频列表 — 默认音色优先, 其次最新完成 (播放器确定性).
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id = #{scriptId} "
          + "ORDER BY is_default DESC, completed_at DESC")
    List<SlidePptPageAudio> listByScript(@Param("scriptId") Long scriptId);

    /**
     * Q-2 (N+1 修复): 批量取多个 script 的所有音频 (1 SQL 取代 N 次 listByScript).
     * 与 listByScript 一致按 is_default DESC, completed_at DESC 排序 (U-5 确定性).
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id IN "
          + "<foreach collection='scriptIds' item='sid' open='(' separator=',' close=')'>"
          + "  #{sid}"
          + "</foreach> "
          + "ORDER BY is_default DESC, completed_at DESC")
    List<SlidePptPageAudio> listByScriptIds(@Param("scriptIds") List<Long> scriptIds);

    /**
     * 按 token 查找 (流式 GET endpoint 用).
     * 不依赖 pageNumber, 与 7-19 P1-C 修复一致.
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE audio_token = #{token} LIMIT 1")
    SlidePptPageAudio findByToken(@Param("token") String token);

    /**
     * 【BUG #9 修复】 批量查询: 一次 SQL 取多个 script 的所有音频, 取代 N+1.
     * 配合 listByPageIds (script mapper) 实现 2 queries per tree.
     */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE script_id IN "
          + "(SELECT id FROM slide_ppt_page_scripts "
          + " WHERE ppt_page_id IN "
          + "   <foreach collection='pageIds' item='pid' open='(' separator=',' close=')'>"
          + "   #{pid}"
          + " </foreach>"
          + " AND is_active = TRUE) "
          + "ORDER BY is_default DESC, completed_at DESC")
    List<SlidePptPageAudio> listByPageIds(@Param("pageIds") List<Long> pageIds);

    // ====== Q-1: TtsWorker 幂等抢占 (V330 worker_id 列) ======

    /**
     * Q-1: 原子抢占待消费行. PostgreSQL UPDATE 行锁 + WHERE 重评估保证:
     * 两个 worker 同时 claim 时, 第二个的 UPDATE 等待第一个提交后重新评估 WHERE,
     * 行已变 PROCESSING → 0 行命中 → 不重复处理 (不重复扣 MiniMax API).
     * 仅抢占: status=GENERATING 且 worker_id 为空 且 插入超 3s (避开事务未提交).
     * generation_started_at 重置为 CURRENT_TIMESTAMP → 超时从 claim 时刻重新计时.
     */
    @Update("UPDATE slide_ppt_page_audios SET status='PROCESSING', worker_id=#{workerId}, "
            + "generation_started_at=CURRENT_TIMESTAMP "
            + "WHERE id IN (SELECT id FROM slide_ppt_page_audios "
            + "  WHERE status='GENERATING' AND (worker_id IS NULL OR worker_id='') "
            + "  AND generation_started_at IS NOT NULL AND generation_started_at < #{before} "
            + "  ORDER BY id LIMIT #{limit})")
    int claimPending(@Param("workerId") String workerId,
                     @Param("before") LocalDateTime before,
                     @Param("limit") int limit);

    /** Q-1: 取回本 worker 抢占到的行 (与 claimPending 配对, 事务提交后可见). */
    @Select("SELECT * FROM slide_ppt_page_audios WHERE status='PROCESSING' AND worker_id = #{workerId} "
            + "ORDER BY id ASC")
    List<SlidePptPageAudio> selectClaimed(@Param("workerId") String workerId);

    /**
     * Q-1: 处理失败 → 回滚 status='GENERATING' (worker_id 清空), 下轮重试.
     * 仅限本 worker 抢占的行 (worker_id 匹配), 防误释放他人抢占.
     */
    @Update("UPDATE slide_ppt_page_audios SET status='GENERATING', worker_id=NULL "
            + "WHERE id = #{id} AND status='PROCESSING' AND worker_id = #{workerId}")
    int releaseClaim(@Param("id") Long id, @Param("workerId") String workerId);

    /**
     * Q-1: 回收崩溃 worker 遗留的 PROCESSING 行 (超过 10 分钟未完成) → 恢复 GENERATING 供下轮重试.
     */
    @Update("UPDATE slide_ppt_page_audios SET status='GENERATING', worker_id=NULL "
            + "WHERE status='PROCESSING' AND generation_started_at < #{before}")
    int reclaimOrphans(@Param("before") LocalDateTime before);

    /**
     * Q-1: 长时间未消费的 GENERATING 行 (超过 10 分钟) → FAILED (保留更具体的 error_message).
     */
    @Update("UPDATE slide_ppt_page_audios SET status='FAILED', "
            + "error_message=COALESCE(error_message, '生成超时（>10 分钟）'), completed_at=CURRENT_TIMESTAMP "
            + "WHERE status='GENERATING' AND generation_started_at IS NOT NULL "
            + "AND generation_started_at < #{timeoutBefore}")
    int markTimedOut(@Param("timeoutBefore") LocalDateTime timeoutBefore);
}

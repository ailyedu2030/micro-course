package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.LearningProgress;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LearningProgressRepository extends BaseMapper<LearningProgress> {

    /**
     * Round 8-4 (P0)：幂等插入学习进度。命中任一唯一约束（uk_lp_user_lesson /
     * uk_lp_user_course_chapter_active）时 ON CONFLICT DO NOTHING 静默跳过，<b>绝不抛异常</b>，
     * 故 PG 事务不会进入 aborted 状态，调用方可安全回查已存在记录并返回 200。
     * 用于消除多设备/并发上报产生的重复进度记录（完成度翻倍）。
     */
    @Insert("INSERT INTO learning_progress (user_id, course_id, chapter_id, lesson_id, video_progress, "
            + "video_position, exercise_completed, exercise_passed, total_watch_time, device_id, platform, "
            + "playback_speed, confidence, offline_attended, completed, last_watch_at, created_at, updated_at, version) "
            + "VALUES (#{userId}, #{courseId}, #{chapterId}, #{sectionId}, #{videoProgress}, #{videoPosition}, "
            + "#{exerciseCompleted}, #{exercisePassed}, #{totalWatchTime}, #{deviceId}, #{platform}, "
            + "#{playbackSpeed}, #{confidence}, #{offlineAttended}, #{completed}, #{lastWatchAt}, #{createdAt}, #{updatedAt}, 0) "
            + "ON CONFLICT DO NOTHING")
    int insertIfAbsent(LearningProgress progress);

    /**
     * S-1（设计决策 3）：evaluateFlow 的 SKIP_IF_KNOWN 规则服务端读取用户课时级学习进度。
     * 不信任客户端 userProgress（可伪造进度绕过 SKIP 规则）；userId 来自 SecurityContext。
     * video_progress 为 0-100 百分比，调用方换算 0.0-1.0。
     */
    @Select("SELECT * FROM learning_progress WHERE user_id = #{userId} AND course_id = #{courseId} "
            + "AND lesson_id = #{sectionId} AND deleted_at IS NULL "
            + "ORDER BY updated_at DESC LIMIT 1")
    LearningProgress findLatestByUserAndLesson(@org.apache.ibatis.annotations.Param("userId") Long userId,
                                               @org.apache.ibatis.annotations.Param("courseId") Long courseId,
                                               @org.apache.ibatis.annotations.Param("sectionId") Long sectionId);

    /**
     * SQL聚合查询总观看时长，避免全表加载到内存（OOM修复）
     */
    @Select("SELECT COALESCE(SUM(total_watch_time), 0) FROM learning_progress WHERE deleted_at IS NULL")
    Long sumTotalWatchTime();

    /**
     * 视频分析 - 唯一观看人数 (按 chapterId)
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM learning_progress WHERE chapter_id = #{chapterId} AND deleted_at IS NULL")
    Long countUniqueViewersByChapterId(Long chapterId);

    /**
     * 视频分析 - 总播放次数 (按 chapterId)
     */
    @Select("SELECT COUNT(*) FROM learning_progress WHERE chapter_id = #{chapterId} AND deleted_at IS NULL")
    Long countByChapterId(Long chapterId);

    /**
     * 视频分析 - 平均观看时长 (秒, 按 chapterId)
     */
    @Select("SELECT COALESCE(AVG(total_watch_time), 0) FROM learning_progress WHERE chapter_id = #{chapterId} AND deleted_at IS NULL")
    Double avgWatchSecondsByChapterId(Long chapterId);

    /**
     * 视频分析 - 完成人数 (按 chapterId)
     */
    @Select("SELECT COUNT(*) FROM learning_progress WHERE chapter_id = #{chapterId} AND completed = TRUE AND deleted_at IS NULL")
    Long countCompletedByChapterId(Long chapterId);

    /**
     * SQL聚合查询总观看时长（秒），支持时间范围过滤 — 替代全量加载到Java内存再求和（OOM修复）。
     * 与 sumTotalWatchTime() 的区别：本方法接受可选的起始时间下限，调用方可传递时间范围缩小扫描行数。
     * 当 since 为 NULL 时行为与 sumTotalWatchTime() 一致（全表SUM）。
     */
    @Select("SELECT COALESCE(SUM(total_watch_time), 0) FROM learning_progress WHERE deleted_at IS NULL "
            + "AND (#{since} IS NULL OR created_at >= #{since})")
    Long sumTotalWatchTimeSince(@org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since);

    /**
     * SQL聚合查询总观看时长（分钟级别，SQL中完成 /60 转换）— 替代 Java 流式 sum/60。
     */
    @Select("SELECT COALESCE(SUM(total_watch_time) / 60, 0) FROM learning_progress WHERE deleted_at IS NULL")
    Long sumTotalWatchTimeMinutes();

    /**
     * SQL聚合：统计指定时间范围内有观看记录的去重用户数 — 替代全量加载 + Java distinct count。
     * @param since 起始时间（含），为 NULL 时不限制
     * @return 去重用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM learning_progress "
            + "WHERE last_watch_at IS NOT NULL AND deleted_at IS NULL "
            + "AND (#{since} IS NULL OR last_watch_at >= #{since})")
    Long countDistinctActiveUsersSince(@org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since);
}
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
            + "VALUES (#{userId}, #{courseId}, #{chapterId}, #{lessonId}, #{videoProgress}, #{videoPosition}, "
            + "#{exerciseCompleted}, #{exercisePassed}, #{totalWatchTime}, #{deviceId}, #{platform}, "
            + "#{playbackSpeed}, #{confidence}, #{offlineAttended}, #{completed}, #{lastWatchAt}, #{createdAt}, #{updatedAt}, 0) "
            + "ON CONFLICT DO NOTHING")
    int insertIfAbsent(LearningProgress progress);

    /**
     * SQL聚合查询总观看时长，避免全表加载到内存（OOM修复）
     */
    @Select("SELECT COALESCE(SUM(total_watch_time), 0) FROM learning_progress WHERE deleted_at IS NULL")
    Long sumTotalWatchTime();
}
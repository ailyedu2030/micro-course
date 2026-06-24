package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EnrollmentRepository extends BaseMapper<Enrollment> {

    /**
     * ★ Round 11-2 性能优化：教师维度选课平均分（单条聚合 SQL）。
     * 替代「先查教师课程 ID 集合 + 全量加载选课记录 + 内存求均值」的全表扫描式实现。
     * 语义等价：仅统计该教师<b>未删除课程</b>下、{@code final_score} 非空且<b>未删除</b>的选课记录均分；
     * 无匹配记录时 AVG 返回 NULL，由调用方兜底为 0。raw SQL 不经 @TableLogic，故 deleted_at 过滤显式书写。
     */
    @Select("SELECT AVG(e.final_score) FROM enrollments e "
            + "JOIN courses c ON e.course_id = c.id "
            + "WHERE c.teacher_id = #{teacherId} "
            + "  AND e.final_score IS NOT NULL "
            + "  AND e.deleted_at IS NULL "
            + "  AND c.deleted_at IS NULL")
    Double avgScoreByTeacherId(@Param("teacherId") Long teacherId);

    /**
     * ★ Round 11-2 性能优化：单门课程选课平均分（单条聚合 SQL）。
     * 替代 computeStats 中「全量加载该课程已评分选课记录 + 内存求均值」。
     * 语义等价：统计该课程下 {@code final_score} 非空且<b>未删除</b>的选课记录均分；无匹配返回 NULL。
     */
    @Select("SELECT AVG(final_score) FROM enrollments "
            + "WHERE course_id = #{courseId} "
            + "  AND final_score IS NOT NULL "
            + "  AND deleted_at IS NULL")
    Double avgScoreByCourseId(@Param("courseId") Long courseId);

    /**
     * Phase 14: 批量统计多门课程已完成选课数（按 course_id 分组）
     * 返回 List of Map, 格式: [{course_id: Long, cnt: Long}, ...]
     */
    @Select("<script>" +
            "SELECT course_id, COUNT(*) AS cnt FROM enrollments " +
            "WHERE deleted_at IS NULL AND status = 'COMPLETED' AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY course_id" +
            "</script>")
    List<Map<String, Object>> countCompletedByCourseIds(@Param("courseIds") List<Long> courseIds);

    /**
     * Phase 14: 批量统计多门课程进行中+已完成选课数（按 course_id 分组）
     */
    @Select("<script>" +
            "SELECT course_id, COUNT(*) AS cnt FROM enrollments " +
            "WHERE deleted_at IS NULL AND status IN ('IN_PROGRESS','COMPLETED') AND course_id IN " +
            "<foreach collection='courseIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY course_id" +
            "</script>")
    List<Map<String, Object>> countInProgressOrCompletedByCourseIds(@Param("courseIds") List<Long> courseIds);

    /**
     * ★ 业务逻辑审计 DEVIATION-1 修复：原子容量检查 + 插入。
     * <p>原实现的 check-then-insert 是非原子的，高并发下可能超 max_students 上限。
     * 本方法在单条 INSERT ... SELECT ... WHERE 中同时检查：</p>
     * <ol>
     *   <li>课程存在且未删除 (c.deleted_at IS NULL)</li>
     *   <li>课程状态为 PUBLISHED (c.status = 4)</li>
     *   <li>学生不存在重复选课（同 user_id+course_id 在同事务中）</li>
     *   <li>未达人数上限 (max_students = 0 OR student_count < max_students)</li>
     * </ol>
     * <p>返回 0 表示不满足条件（满员/已选/未发布），由调用方转 WAITLIST 或抛异常。</p>
     */
    @org.apache.ibatis.annotations.Insert("INSERT INTO enrollments " +
            "(user_id, course_id, enrollment_status, source_channel, progress, completed, enrolled_at, updated_at, version) " +
            "SELECT #{userId}, #{courseId}, #{status}, #{sourceChannel}, 0, false, NOW(), NOW(), 0 " +
            "FROM courses c " +
            // 客户体验修复 v1.7.0: 接受 status IN (2,4) — APPROVED(2) 管理员已通过 + PUBLISHED(4) 教师已发布
            // 旧硬编码 status=4 会卡死所有 5 门核心 seed 课程(状态=2,published_at 已设)
            "WHERE c.id = #{courseId} AND c.deleted_at IS NULL AND c.status IN (2, 4) " +
            "  AND (c.max_students IS NULL OR c.max_students = 0 " +
            "       OR COALESCE(c.student_count, 0) < c.max_students) " +
            "  AND NOT EXISTS (SELECT 1 FROM enrollments e " +
            "                  WHERE e.user_id = #{userId} AND e.course_id = #{courseId})")
    int atomicInsertIfCapacity(@Param("userId") Long userId,
                               @Param("courseId") Long courseId,
                               @Param("status") String status,
                               @Param("sourceChannel") String sourceChannel);

    /**
     * ★ 业务逻辑审计 P1 修复：候补队列插入（绕过容量检查，但保证不重复）。
     * <p>当课程已满员时调用此方法创建 WAITLIST 记录。
     * 仍受 UNIQUE(user_id, course_id) 约束和 NOT EXISTS 防止重复。</p>
     */
    @org.apache.ibatis.annotations.Insert("INSERT INTO enrollments " +
            "(user_id, course_id, enrollment_status, source_channel, progress, completed, enrolled_at, updated_at, version) " +
            "SELECT #{userId}, #{courseId}, #{status}, #{sourceChannel}, 0, false, NOW(), NOW(), 0 " +
            "FROM courses c " +
            // 客户体验修复 v1.7.0: 与 atomicInsertIfCapacity 保持一致
            "WHERE c.id = #{courseId} AND c.deleted_at IS NULL AND c.status IN (2, 4) " +
            "  AND NOT EXISTS (SELECT 1 FROM enrollments e " +
            "                  WHERE e.user_id = #{userId} AND e.course_id = #{courseId})")
    int atomicInsertIfEnrollable(@Param("userId") Long userId,
                                 @Param("courseId") Long courseId,
                                 @Param("status") String status,
                                 @Param("sourceChannel") String sourceChannel);

    /**
     * 统计某课程 WAITLIST 状态的学生数（用于计算候补位置）
     */
    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM enrollments " +
            "WHERE course_id = #{courseId} AND enrollment_status = 'WAITLIST'")
    int countWaitlistByCourseId(@Param("courseId") Long courseId);

    /**
     * 客户体验修复 v1.7.0: 找课程的有效选课学生 (用于下架通知)
     * 返回所有 ENROLLED/IN_PROGRESS/COMPLETED 状态的学生 user_id
     * (WAITLIST 已被拒, DROPPED/CANCELLED 已退出, 都不需要通知)
     */
    @org.apache.ibatis.annotations.Select("SELECT DISTINCT user_id FROM enrollments " +
            "WHERE course_id = #{courseId} " +
            "  AND deleted_at IS NULL " +
            "  AND enrollment_status IN ('ENROLLED', 'IN_PROGRESS', 'COMPLETED')")
    List<Long> findActiveUserIdsByCourseId(@Param("courseId") Long courseId);
}

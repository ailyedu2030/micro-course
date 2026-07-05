package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface CourseRepository extends BaseMapper<Course> {

    /**
     * 原子增加课程选课人数（Round 8-4 P0 修复：选课/取消与 student_count 同步）。
     * 用 SQL 原子自增避免并发读-改-写丢失；COALESCE 兜底 NULL。
     */
    @Update("UPDATE courses SET student_count = COALESCE(student_count, 0) + 1, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{courseId} AND deleted_at IS NULL")
    int atomicIncrementStudentCount(@Param("courseId") Long courseId);

    /**
     * ★ 业务逻辑审计 DEVIATION-1 修复：原子容量保护增计数。
     * <p>仅当未达 max_students 上限或不限人数（max=0）时增 1，否则返回 0（不入账）。
     * 与 {@link com.microcourse.repository.EnrollmentRepository#atomicInsertIfCapacity} 配合实现"插入+计数"双闸门。</p>
     */
    @Update("UPDATE courses SET student_count = COALESCE(student_count, 0) + 1, " +
            "updated_at = CURRENT_TIMESTAMP " +
            "WHERE id = #{courseId} AND deleted_at IS NULL " +
            "  AND (max_students IS NULL OR max_students = 0 OR COALESCE(student_count, 0) < max_students)")
    int atomicIncrementIfNotFull(@Param("courseId") Long courseId);

    /**
     * ★ 业务逻辑审计 P0-1 压测发现追加：行级锁查询课程。
     * <p>SELECT ... FOR UPDATE 锁定该行直到事务提交，
     * 防止并发 enroll() 同时读到过时的 student_count 造成超卖。</p>
     * <p>压测验证 (50 并发对 max=5 课程): 锁定前 11/15 超卖; 锁定后 5/15 满员触发 WAITLIST。</p>
     */
    @org.apache.ibatis.annotations.Select("SELECT id, status, max_students, COALESCE(student_count, 0) AS student_count, deleted_at " +
            "FROM courses WHERE id = #{courseId} FOR UPDATE")
    java.util.Map<String, Object> selectByIdForUpdate(@Param("courseId") Long courseId);

    /**
     * 原子减少课程选课人数（Round 8-4 P0 修复）。
     * GREATEST 兜底避免出现负数；COALESCE 兜底 NULL。
     */
    @Update("UPDATE courses SET student_count = GREATEST(COALESCE(student_count, 0) - 1, 0), " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = #{courseId} AND deleted_at IS NULL")
    int atomicDecrementStudentCount(@Param("courseId") Long courseId);

    /**
     * 原子更新课程平均评分（Round 11-4 安全修复：消除字符串拼接 SQL 注入风险）。
     * 原实现使用 {@code LambdaUpdateWrapper.setSql("... course_id = " + courseId + " ...")}
     * 字符串拼接，存在 SQL 注入隐患。改为 MyBatis {@code #{courseId}} 参数化预编译占位符，
     * 与 {@link #atomicIncrementStudentCount} 同模式。COALESCE 兜底无评价时 avg_rating=0，
     * 保持原 read-compute-write 原子语义不变；WHERE 加 deleted_at IS NULL 与逻辑删除一致。
     */
    @Update("UPDATE courses SET avg_rating = (" +
            "SELECT COALESCE(AVG(rating), 0) FROM course_reviews " +
            "WHERE course_id = #{courseId} AND deleted_at IS NULL AND status = 1" +
            ") WHERE id = #{courseId} AND deleted_at IS NULL")
    int updateAvgRating(@Param("courseId") Long courseId);

    /**
     * 全校平均完成率：已发布课程（status=2）关联 enrollments，计算 completed=true 比例的 AVG
     */
    @Select("SELECT COALESCE(AVG(completion_rate), 0) FROM (" +
            "  SELECT c.id, CASE WHEN COUNT(e.id) = 0 THEN 0 " +
            "       ELSE COUNT(CASE WHEN e.completed = true THEN 1 END) * 100.0 / COUNT(e.id) END AS completion_rate " +
            "  FROM courses c " +
            "  LEFT JOIN enrollments e ON e.course_id = c.id AND e.deleted_at IS NULL " +
            "  WHERE c.status = 2 AND c.deleted_at IS NULL " +
            "  GROUP BY c.id" +
            ") AS sub")
    Double selectAvgCompletionRate();

    /**
     * 当前学期：取最新的 semester 值
     */
    @Select("SELECT semester FROM courses WHERE deleted_at IS NULL AND semester IS NOT NULL ORDER BY created_at DESC LIMIT 1")
    String selectCurrentSemester();

    /**
     * 按院系分组聚合统计：开课数、选课人次、学生总数、平均完成率、平均正确率
     * 使用 teaching_classes 关联学生数，enrollments 关联选课人次
     */
    @Select("SELECT " +
            "  d.id AS department_id, " +
            "  d.name AS department_name, " +
            "  COUNT(DISTINCT c.id) AS course_count, " +
            "  COUNT(DISTINCT e.id) AS enrollment_count, " +
            "  COUNT(DISTINCT u.id) AS student_count, " +
            "  COALESCE(AVG(completion_rate), 0) AS avg_completion_rate, " +
            "  COALESCE(AVG(accuracy_rate), 0) AS avg_accuracy_rate " +
            "FROM departments d " +
            "LEFT JOIN courses c ON c.offer_department_id = d.id AND c.status = 2 AND c.deleted_at IS NULL " +
            "LEFT JOIN enrollments e ON e.course_id = c.id AND e.deleted_at IS NULL " +
            "LEFT JOIN users u ON u.department_id = d.id AND u.role = 'STUDENT' AND u.deleted_at IS NULL " +
            "LEFT JOIN (" +
            "  SELECT c.id AS course_id, " +
            "    CASE WHEN COUNT(e2.id) = 0 THEN 0 " +
            "         ELSE COUNT(CASE WHEN e2.completed = true THEN 1 END) * 100.0 / COUNT(e2.id) END AS completion_rate, " +
            "    COALESCE(AVG(er.score * 100.0 / NULLIF(er.total_score, 0)), 0) AS accuracy_rate " +
            "  FROM courses c " +
            "  LEFT JOIN enrollments e2 ON e2.course_id = c.id AND e2.deleted_at IS NULL " +
            "  LEFT JOIN exercise_records er ON er.user_id = e2.user_id " +
            "  WHERE c.status = 2 AND c.deleted_at IS NULL " +
            "  GROUP BY c.id" +
            ") AS course_stats ON course_stats.course_id = c.id " +
            "GROUP BY d.id, d.name " +
            "ORDER BY avg_completion_rate DESC")
    List<Map<String, Object>> selectDepartmentStats();

    /**
     * 院系详情：查询该院系下每门课程的统计
     */
    @Select("SELECT " +
            "  c.id AS course_id, " +
            "  c.title AS course_title, " +
            "  COALESCE(u.real_name, '') AS teacher_name, " +
            "  COUNT(DISTINCT e.id) AS enrollment_count, " +
            "  CASE WHEN COUNT(e.id) = 0 THEN 0 " +
            "       ELSE COUNT(CASE WHEN e.completed = true THEN 1 END) * 100.0 / COUNT(e.id) END AS completion_rate, " +
            "  COALESCE(AVG(er.score * 100.0 / NULLIF(er.total_score, 0)), 0) AS avg_score " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id AND u.deleted_at IS NULL " +
            "LEFT JOIN enrollments e ON e.course_id = c.id AND e.deleted_at IS NULL " +
            "LEFT JOIN exercise_records er ON er.user_id = e.user_id " +
            "WHERE c.offer_department_id = #{departmentId} AND c.status = 2 AND c.deleted_at IS NULL " +
            "GROUP BY c.id, c.title, u.real_name " +
            "ORDER BY c.created_at DESC")
    List<Map<String, Object>> selectCourseStatsByDepartment(@Param("departmentId") Long departmentId);

    /**
     * 完成率预警：完成率 < 30% 的课程
     */
    @Select("SELECT " +
            "  c.id AS course_id, " +
            "  c.title AS course_title, " +
            "  COALESCE(u.real_name, '') AS teacher_name, " +
            "  COUNT(e.id) AS enrollment_count, " +
            "  CASE WHEN COUNT(e.id) = 0 THEN 0 " +
            "       ELSE COUNT(CASE WHEN e.completed = true THEN 1 END) * 100.0 / COUNT(e.id) END AS completion_rate " +
            "FROM courses c " +
            "LEFT JOIN users u ON u.id = c.teacher_id AND u.deleted_at IS NULL " +
            "LEFT JOIN enrollments e ON e.course_id = c.id AND e.deleted_at IS NULL " +
            "WHERE c.status = 2 AND c.deleted_at IS NULL " +
            "GROUP BY c.id, c.title, u.real_name " +
            "HAVING COUNT(e.id) > 0 AND COUNT(CASE WHEN e.completed = true THEN 1 END) * 100.0 / COUNT(e.id) < 30 " +
            "ORDER BY completion_rate ASC")
    List<Map<String, Object>> selectCompletionWarnings();

    /**
     * 参与率趋势：按月统计选课人数占全校学生的比例
     * @param semester 可选，如 "2025-1"
     */
    @Select("<script>" +
            "SELECT " +
            "  TO_CHAR(e.enrolled_at, 'YYYY-MM') AS month, " +
            "  CASE WHEN total_students = 0 THEN 0 " +
            "       ELSE COUNT(DISTINCT e.user_id) * 1.0 / total_students END AS participation_rate " +
            "FROM enrollments e " +
            "CROSS JOIN (SELECT COUNT(*) AS total_students FROM users WHERE role = 'STUDENT' AND deleted_at IS NULL) AS s " +
            "WHERE e.deleted_at IS NULL " +
            "<if test='startDate != null'>" +
            "AND e.enrolled_at >= CAST(#{startDate} AS TIMESTAMP) " +
            "</if>" +
            "<if test='endDate != null'>" +
            "AND e.enrolled_at &lt; CAST(#{endDate} AS TIMESTAMP) " +
            "</if>" +
            "GROUP BY TO_CHAR(e.enrolled_at, 'YYYY-MM'), total_students " +
            "ORDER BY month ASC" +
            "</script>")
    List<Map<String, Object>> selectParticipationTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 完成率趋势：按月统计已完成课程的比例
     * @param semester 可选，如 "2025-1"
     */
    @Select("<script>" +
            "SELECT " +
            "  month, " +
            "  CASE WHEN total_enrollments = 0 THEN 0 " +
            "       ELSE completed_count * 100.0 / total_enrollments END AS completion_rate " +
            "FROM (" +
            "  SELECT " +
            "    TO_CHAR(e.enrolled_at, 'YYYY-MM') AS month, " +
            "    COUNT(e.id) AS total_enrollments, " +
            "    COUNT(CASE WHEN e.completed = true THEN 1 END) AS completed_count " +
            "  FROM enrollments e " +
            "  WHERE e.deleted_at IS NULL " +
            "  <if test='startDate != null'>" +
            "  AND e.enrolled_at >= CAST(#{startDate} AS TIMESTAMP) " +
            "  </if>" +
            "  <if test='endDate != null'>" +
            "  AND e.enrolled_at &lt; CAST(#{endDate} AS TIMESTAMP) " +
            "  </if>" +
            "  GROUP BY TO_CHAR(e.enrolled_at, 'YYYY-MM')" +
            ") AS sub " +
            "ORDER BY month ASC" +
            "</script>")
    List<Map<String, Object>> selectCompletionTrend(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.TeacherRating;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeacherRatingRepository extends BaseMapper<TeacherRating> {

    /**
     * 批量统计教师维度数据：课程数、选课数、完成率、平均评分
     * 返回 [teacher_id, course_count, student_count, completion_rate, avg_rating]
     */
    @Select("SELECT " +
            "  u.id AS teacher_id, " +
            "  COALESCE(c.course_count, 0) AS course_count, " +
            "  COALESCE(e.student_count, 0) AS student_count, " +
            "  COALESCE(e.completion_rate, 0) AS completion_rate, " +
            "  COALESCE(r.avg_rating, 0) AS avg_rating " +
            "FROM users u " +
            "LEFT JOIN (SELECT teacher_id, COUNT(*) AS course_count FROM courses WHERE deleted_at IS NULL GROUP BY teacher_id) c ON u.id = c.teacher_id " +
            "LEFT JOIN (SELECT c2.teacher_id, COUNT(DISTINCT e2.user_id) AS student_count, " +
            "                  CASE WHEN COUNT(DISTINCT e2.user_id) > 0 " +
            "                       THEN COUNT(DISTINCT CASE WHEN e2.completed THEN e2.user_id END) * 100.0 / COUNT(DISTINCT e2.user_id) " +
            "                       ELSE 0 END AS completion_rate " +
            "           FROM enrollments e2 JOIN courses c2 ON e2.course_id = c2.id AND c2.deleted_at IS NULL " +
            "           WHERE e2.deleted_at IS NULL GROUP BY c2.teacher_id) e ON u.id = e.teacher_id " +
            "LEFT JOIN (SELECT c3.teacher_id, AVG(cr.rating) AS avg_rating " +
            "           FROM course_reviews cr JOIN courses c3 ON cr.course_id = c3.id AND c3.deleted_at IS NULL " +
            "           WHERE cr.deleted_at IS NULL GROUP BY c3.teacher_id) r ON u.id = r.teacher_id " +
            "WHERE u.role = 'TEACHER' AND u.deleted_at IS NULL")
    List<TeacherRatingStatRow> selectTeacherStats();

    /**
     * 统计行映射接口
     */
    interface TeacherRatingStatRow {
        Long getTeacherId();
        int getCourseCount();
        int getStudentCount();
        double getCompletionRate();
        double getAvgRating();
    }

    /**
     * 批量更新教师评级（INSERT ON CONFLICT）
     */
    @Insert("INSERT INTO teacher_ratings " +
            "(teacher_id, rating_score, tier, avg_student_rating, completion_rate, total_students, total_courses, calculated_at, created_at, updated_at) " +
            "VALUES (#{teacherId}, #{score}, #{tier}, #{avgRating}, #{completionRate}, #{totalStudents}, #{totalCourses}, NOW(), NOW(), NOW()) " +
            "ON CONFLICT (teacher_id) DO UPDATE SET " +
            "  rating_score = EXCLUDED.rating_score, " +
            "  tier = EXCLUDED.tier, " +
            "  avg_student_rating = EXCLUDED.avg_student_rating, " +
            "  completion_rate = EXCLUDED.completion_rate, " +
            "  total_students = EXCLUDED.total_students, " +
            "  total_courses = EXCLUDED.total_courses, " +
            "  calculated_at = EXCLUDED.calculated_at, " +
            "  updated_at = NOW()")
    int upsertRating(@Param("teacherId") Long teacherId,
                     @Param("score") java.math.BigDecimal score,
                     @Param("tier") String tier,
                     @Param("avgRating") java.math.BigDecimal avgRating,
                     @Param("completionRate") java.math.BigDecimal completionRate,
                     @Param("totalStudents") int totalStudents,
                     @Param("totalCourses") int totalCourses);
}

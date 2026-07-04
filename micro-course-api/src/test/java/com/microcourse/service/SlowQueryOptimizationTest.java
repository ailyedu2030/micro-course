package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.ExerciseVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.PendingTaskVO;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.QuestionRepository;
import com.microcourse.service.EnrollmentService;
import com.microcourse.service.ExerciseService;
import com.microcourse.service.TeacherService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 11-2 · 慢 SQL 深度优化验证。
 *
 * <p>通过 {@link SpyBean} 监视 MyBatis Mapper 的调用次数，证明三处慢查询已由
 * 「全量加载 / 内存装配 / 逐门课程 N+1」改为「单条聚合 SQL / 批量预加载 / 数据库子查询」：</p>
 * <ol>
 *   <li>{@code EnrollmentService.getAvgScoreByTeacherId}：走单条聚合 {@code avgScoreByTeacherId}，
 *       不再 {@code selectList} 全量加载选课记录。</li>
 *   <li>{@code TeacherService.getPendingTasks}：课程 ID 集合仅 1 次查询，且<b>从不</b>逐门课程查 {@code questions}。</li>
 *   <li>{@code ExerciseService.page}（教师视角）：用 SQL 子查询过滤课程，不再 {@code selectList} 把课程 ID 装入内存。</li>
 * </ol>
 * 真实 Postgres（JdbcTemplate 直插，满足 NOT NULL + FK 约束）执行，断言查询次数与功能正确性。
 */
@DisplayName("Round11-2 慢SQL优化")
class SlowQueryOptimizationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private TeacherService teacherService;
    @Autowired
    private ExerciseService exerciseService;

    @SpyBean
    private EnrollmentRepository enrollmentRepository;
    @SpyBean
    private QuestionRepository questionRepository;
    @SpyBean
    private CourseRepository courseRepository;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdExerciseIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // FK 安全顺序：enrollments / exercises（FK course_id ON DELETE CASCADE，仍显式先删）→ courses → users → categories
        for (Long c : createdCourseIds) {
            try { jdbc.update("DELETE FROM enrollments WHERE course_id = ?", c); } catch (Exception ignored) {}
        }
        for (Long e : createdExerciseIds) {
            try { jdbc.update("DELETE FROM exercises WHERE id = ?", e); } catch (Exception ignored) {}
        }
        for (Long c : createdCourseIds) {
            try { jdbc.update("DELETE FROM courses WHERE id = ?", c); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        for (Long cat : createdCategoryIds) {
            try { jdbc.update("DELETE FROM course_categories WHERE id = ?", cat); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
        createdCourseIds.clear();
        createdCategoryIds.clear();
        createdExerciseIds.clear();
        SecurityContextHolder.clearContext();
    }

    // --------- fixtures ---------

    private Long insertCategory() {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                        "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "r11-cat-" + System.nanoTime());
        createdCategoryIds.add(id);
        return id;
    }

    private Long insertUser(String role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, "r11-" + role.toLowerCase() + "-" + System.nanoTime(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv", "R11测试-" + role, role);
        createdUserIds.add(id);
        return id;
    }

    private Long insertCourse(Long categoryId, Long teacherId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 4, true, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, "r11-course-" + System.nanoTime(), categoryId, teacherId);
        createdCourseIds.add(id);
        return id;
    }

    private void insertEnrollment(Long courseId, Long userId, double finalScore) {
        jdbc.update(
                "INSERT INTO enrollments(course_id, user_id, final_score, enrollment_status, enrolled_at, updated_at) " +
                        "VALUES (?, ?, ?, 'APPROVED', now(), now())",
                courseId, userId, finalScore);
    }

    private Long insertExercise(Long courseId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO exercises(course_id, title, pass_score, time_limit, max_attempts, total_score, question_count, version, created_at, updated_at) " +
                        "VALUES (?, ?, 60, 0, 0, 100, 1, 0, now(), now()) RETURNING id",
                Long.class, courseId, "r11-exercise-" + System.nanoTime());
        createdExerciseIds.add(id);
        return id;
    }

    // --------- 1 · 教师平均分：单条聚合 SQL，不再全量加载选课 ---------
    @Test
    @DisplayName("1·avgScore 走单条聚合 SQL，不 selectList 全量加载选课")
    void avgScoreShouldUseAggregateQueryNotLoadAllEnrollments() {
        Long cat = insertCategory();
        Long teacher = insertUser("TEACHER");
        Long course = insertCourse(cat, teacher);
        // 5 名学生 × 已评分选课（60/70/80/90/100 → 平均 80），唯一约束要求 (user_id, course_id) 互异
        double[] scores = {60, 70, 80, 90, 100};
        for (double s : scores) {
            Long student = insertUser("STUDENT");
            insertEnrollment(course, student, s);
        }

        Mockito.clearInvocations(enrollmentRepository);
        double avg = enrollmentService.getAvgScoreByTeacherId(teacher);

        assertEquals(80.0, avg, 0.01, "5 条评分 60/70/80/90/100 的平均分应为 80");
        // 证明走聚合：avgScoreByTeacherId 恰好 1 次；且全程未 selectList 全量加载选课记录
        verify(enrollmentRepository, times(1)).avgScoreByTeacherId(teacher);
        verify(enrollmentRepository, never()).selectList(any());
    }

    // --------- 2 · 待办：批量预加载，绝不逐门课程查 questions ---------
    @Test
    @DisplayName("2·getPendingTasks 课程ID仅1次查询，且从不逐门课程查 questions")
    void pendingTasksShouldNotQueryPerCourse() {
        Long cat = insertCategory();
        Long teacher = insertUser("TEACHER");
        // 10 门课程：若存在 per-course N+1，课程查询/题目查询将随课程数线性增长
        for (int i = 0; i < 10; i++) {
            insertCourse(cat, teacher);
        }

        Mockito.clearInvocations(courseRepository, questionRepository);
        List<PendingTaskVO> tasks = teacherService.getPendingTasks(teacher, 20);

        assertNotNull(tasks, "待办列表不应为 null");
        // 课程 ID 集合：批量 1 次（非每门课程 1 次）
        verify(courseRepository, times(1)).selectList(any());
        // 绝不逐门课程查询题库（task 中「修改前」的 per-course questionRepository 反模式）
        verify(questionRepository, never()).selectList(any());
    }

    // --------- 3 · 教师练习列表：数据库子查询，不把课程ID装入内存 ---------
    @Test
    @DisplayName("3·教师 exercisePage 用子查询，不 selectList 装载课程ID到内存")
    void exercisePageShouldUseSubqueryForTeacher() {
        Long cat = insertCategory();
        Long teacher = insertUser("TEACHER");
        Long course = insertCourse(cat, teacher);
        Long exerciseId = insertExercise(course);

        // 以教师身份调用：principal 必须为 Long，授予 ROLE_TEACHER
        var auth = new UsernamePasswordAuthenticationToken(
                teacher, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Mockito.clearInvocations(courseRepository);
        PageResult<ExerciseVO> result = exerciseService.page(null, null, null, 0, 10);

        assertNotNull(result, "分页结果不应为 null");
        assertTrue(result.getTotalElements() >= 1, "教师应至少看到自己课程下的 1 个练习");
        assertTrue(result.getItems().stream().anyMatch(v -> exerciseId.equals(v.getId())),
                "结果应包含该教师课程下新建的练习");
        // 证明用子查询：教师分支不再 selectList 把课程 ID 装入内存（仅 inSql 子查询 + selectBatchIds 装配）
        verify(courseRepository, never()).selectList(any());
    }
}

package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 跨教师数据隔离回归测试。
 *
 * <p>覆盖：</p>
 * <ol>
 *   <li>两个 progress 入口的跨教师过滤（EnrollmentController + StudentController）</li>
 *   <li>ADMIN/ACADEMIC 全量语义（可跨教师查看）</li>
 *   <li>ACADEMIC 添加/移除学员成功</li>
 *   <li>TEACHER 向他课添加/移除学员 → 被拒</li>
 * </ol>
 *
 * <p>依赖 p0-seed.sql 种子：teacher(id=6) + student(id=7) + courses(id=1..4)。</p>
 */
@DisplayName("P0 跨教师数据隔离")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EnrollmentDataIsolationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();

    @BeforeEach
    void ensureAcademicUsers() {
        // P0-12 fix (2026-07-26 总工程师兜底): 清空 student(7) 残留的 enrollments +
        // enrollment_histories, 避免跨测试类污染 (前序测试遗留 enrollments(7, ?)
        // 会导致 admin_EnrollmentProgress_AllCourses 期望 course-1 但实际响应
        // 仅含其它课程, 因为 (7, 1) 被 status=CANCELLED 标记污染). 仅删 student
        // (7) 不影响其它测试用户.
        jdbc.update("DELETE FROM enrollment_histories WHERE enrollment_id IN "
                + "(SELECT id FROM enrollments WHERE user_id = 7)");
        jdbc.update("DELETE FROM enrollments WHERE user_id = 7");

        // ACADEMIC 用户 9001/9002/9003（已被移出全局 p0-seed，仅此测试类使用）
        // 需先于测试通过 UserStatusCheckFilter 校验
        // 注意：ON CONFLICT (id) 无法处理 uk_users_username 冲突，故先 delete 再 insert
        for (long id : new long[]{9001L, 9002L, 9003L}) {
            jdbc.update("DELETE FROM enrollment_histories WHERE enrollment_id IN "
                    + "(SELECT id FROM enrollments WHERE user_id = ?)", id);
            jdbc.update("DELETE FROM enrollments WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
            jdbc.update("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                            + "VALUES (?, ?, "
                            + "'$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', "
                            + "?, 'ACADEMIC', 1, FALSE, now(), now())",
                    id,
                    id == 9001L ? "p0_academic_view" : (id == 9002L ? "p0_academic_add" : "p0_academic_rm"),
                    id == 9001L ? "ACADEMIC查看" : (id == 9002L ? "ACADEMIC添加" : "ACADEMIC移除"));
            createdUserIds.add(id);
        }
    }

    @AfterEach
    void cleanup() {
        // 清理按依赖逆序
        for (Long c : createdCourseIds) {
            try { jdbc.update("DELETE FROM course_sections WHERE course_id = ?", c); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM course_chapters WHERE course_id = ?", c); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM enrollments WHERE course_id = ?", c); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", c); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM enrollment_histories WHERE enrollment_id IN "
                    + "(SELECT id FROM enrollments WHERE user_id = ?)", u); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM enrollments WHERE user_id = ?", u); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        createdCourseIds.clear();
        createdUserIds.clear();
    }

    // ---- fixture helpers ----

    private Long insertTeacher(String username, String realName) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, department_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'TEACHER', 1, FALSE, 1, now(), now()) RETURNING id",
                Long.class, username,
                "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK",
                realName);
        createdUserIds.add(id);
        return id;
    }

    private Long insertCourse(Long teacherId, String title) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) "
                        + "VALUES (?, 1, ?, 4, TRUE, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, title, teacherId);
        createdCourseIds.add(id);
        return id;
    }

    private Long insertEnrollment(Long userId, Long courseId, String status) {
        // 防御性清理：移除可能因跨测试类环境遗留的 enrollment 记录，避免 uk_enrollments_user_course 冲突
        jdbc.update("DELETE FROM enrollments WHERE user_id = ? AND course_id = ?", userId, courseId);
        return jdbc.queryForObject(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) "
                        + "VALUES (?, ?, 0, FALSE, ?, now(), now()) RETURNING id",
                Long.class, courseId, userId, status);
    }

    private String tokenFor(Long userId, String username, UserRole role) {
        return "Bearer " + jwtUtil.generateToken(userId, username, role, null);
    }

    private String tokenAdmin() throws Exception {
        return "Bearer " + loginAs("admin", "admin123");
    }

    /** p0-seed teacher(id=6) password = student123 */
    private String tokenTeacherA() throws Exception {
        return "Bearer " + loginAs("p0_teacher", "student123");
    }

    // ====================================================================
    //  Scenario: 两个 progress 入口 × 跨教师过滤
    // ====================================================================

    /**
     * 步骤：
     *   1. 创建 teacherB + courseB
     *   2. 将 student(id=7) 分别选入 teacherA 的 course-1 和 teacherB 的 courseB
     *   3. teacherA 调 GET /api/enrollments/student/{7}/progress
     *   → 断言仅返回 courseId=1（teacherA 自己的课程）
     */
    @Test
    @DisplayName("1·TEACHER(EnrollmentController→progress) 仅自己课程")
    void teacherA_EnrollmentProgress_OnlyOwnCourse() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b1", "隔离教师B1");
        Long courseB = insertCourse(teacherB, "隔离B课程1");
        insertEnrollment(7L, 1L, "APPROVED");    // teacherA 的课
        insertEnrollment(7L, courseB, "APPROVED"); // teacherB 的课

        MvcResult result = mockMvc.perform(
                        get("/api/enrollments/student/{userId}/progress", 7L)
                                .header("Authorization", tokenTeacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Integer> courseIds = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data[*].courseId");
        assertTrue(courseIds.contains(1),
                "应含 teacherA 课程 course-1, 实际=" + courseIds);
        assertTrue(courseIds.stream().noneMatch(id -> id.equals(courseB.intValue())),
                "不应含 teacherB 课程 courseId=" + courseB + ", 实际=" + courseIds);
    }

    /**
     * 同场景 1，但走 StudentController 入口。
     */
    @Test
    @DisplayName("2·TEACHER(StudentController→progress) 仅自己课程")
    void teacherA_StudentProgress_OnlyOwnCourse() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b2", "隔离教师B2");
        Long courseB = insertCourse(teacherB, "隔离B课程2");
        insertEnrollment(7L, 1L, "APPROVED");
        insertEnrollment(7L, courseB, "APPROVED");

        MvcResult result = mockMvc.perform(
                        get("/api/students/{userId}/progress", 7L)
                                .header("Authorization", tokenTeacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Integer> courseIds = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data[*].courseId");
        assertTrue(courseIds.contains(1),
                "应含 teacherA 课程 course-1, 实际=" + courseIds);
        assertTrue(courseIds.stream().noneMatch(id -> id.equals(courseB.intValue())),
                "不应含 teacherB 课程 courseId=" + courseB + ", 实际=" + courseIds);
    }

    /**
     * ADMIN 调 progress → 跨教师全量。
     */
    @Test
    @DisplayName("3·ADMIN(progress) 跨教师全量")
    void admin_EnrollmentProgress_AllCourses() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b3", "隔离教师B3");
        Long courseB = insertCourse(teacherB, "隔离B课程3");
        insertEnrollment(7L, 1L, "APPROVED");
        insertEnrollment(7L, courseB, "APPROVED");

        MvcResult result = mockMvc.perform(
                        get("/api/enrollments/student/{userId}/progress", 7L)
                                .header("Authorization", tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Integer> courseIds = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data[*].courseId");
        assertTrue(courseIds.contains(1),
                "应含 teacherA 课程 course-1, 实际=" + courseIds);
        assertTrue(courseIds.contains(courseB.intValue()),
                "应含 teacherB 课程 courseId=" + courseB + ", 实际=" + courseIds);
    }

    /**
     * ACADEMIC 调 progress → 跨教师全量。
     */
    @Test
    @DisplayName("4·ACADEMIC(progress) 跨教师全量")
    void academic_EnrollmentProgress_AllCourses() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b4", "隔离教师B4");
        Long courseB = insertCourse(teacherB, "隔离B课程4");
        insertEnrollment(7L, 1L, "APPROVED");
        insertEnrollment(7L, courseB, "APPROVED");

        String token = tokenFor(9001L, "p0_academic_view", UserRole.ACADEMIC);

        MvcResult result = mockMvc.perform(
                        get("/api/enrollments/student/{userId}/progress", 7L)
                                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<Integer> courseIds = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data[*].courseId");
        assertTrue(courseIds.contains(1),
                "应含 teacherA 课程 course-1, 实际=" + courseIds);
        assertTrue(courseIds.contains(courseB.intValue()),
                "应含 teacherB 课程 courseId=" + courseB + ", 实际=" + courseIds);
    }

    /**
     * teacherA 查与之无选课关联的学生 → assertStudentInTeachersCourses 抛 403。
     * 学生仅选 teacherB 的课，teacherA 无交集。
     */
    @Test
    @DisplayName("5·TEACHER 查无关联学生→403")
    void teacher_StudentNotInTeachersCourses_Returns403() throws Exception {
        // 防御性清理：确保无遗留 enrollment (7,1) 数据污染测试隔离性
        jdbc.update("DELETE FROM enrollments WHERE user_id = 7");
        Long teacherB = insertTeacher("tchr_iso_b5", "隔离教师B5");
        Long courseB = insertCourse(teacherB, "隔离B课程5");
        insertEnrollment(7L, courseB, "APPROVED"); // 仅 teacherB 的课

        mockMvc.perform(get("/api/enrollments/student/{userId}/progress", 7L)
                        .header("Authorization", tokenTeacherA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // ====================================================================
    //  Scenario: CourseStudent endpoints × ACADEMIC / TEACHER
    // ====================================================================

    @Test
    @DisplayName("6·ACADEMIC 添加学员→200")
    void academic_AddStudentToCourse_Success() throws Exception {
        String token = tokenFor(9002L, "p0_academic_add", UserRole.ACADEMIC);

        mockMvc.perform(post("/api/courses/{courseId}/students/{userId}", 1L, 7L)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("7·ACADEMIC 移除学员→200")
    void academic_RemoveStudentFromCourse_Success() throws Exception {
        insertEnrollment(7L, 1L, "APPROVED");

        // P0 修复: 使用真实 ACADEMIC token 9003 而非 ADMIN 替代
        // 校验 ACADEMIC 通过 CourseStudentController 删除学员不被 cancelEnrollment 二次拒绝
        String token = tokenFor(9003L, "p0_academic_rm", UserRole.ACADEMIC);
        mockMvc.perform(delete("/api/courses/{courseId}/students/{userId}", 1L, 7L)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("8·TEACHER 向他课添加学员→403")
    void teacher_AddStudentToOthersCourse_Forbidden() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b8", "隔离教师B8");
        Long courseB = insertCourse(teacherB, "隔离B课程8");

        mockMvc.perform(post("/api/courses/{courseId}/students/{userId}", courseB, 7L)
                        .header("Authorization", tokenTeacherA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("9·TEACHER 从他课移除学员→403")
    void teacher_RemoveStudentFromOthersCourse_Forbidden() throws Exception {
        Long teacherB = insertTeacher("tchr_iso_b9", "隔离教师B9");
        Long courseB = insertCourse(teacherB, "隔离B课程9");
        insertEnrollment(7L, courseB, "APPROVED");

        mockMvc.perform(delete("/api/courses/{courseId}/students/{userId}", courseB, 7L)
                        .header("Authorization", tokenTeacherA()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

}

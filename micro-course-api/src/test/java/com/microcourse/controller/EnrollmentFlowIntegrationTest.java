package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Phase B-3 · 链路 2 · 选课流程集成测试（8 用例）。
 *
 * <p>断言对齐实际实现（EnrollmentServiceImpl / EnrollmentController / ErrorCode）：</p>
 * <ul>
 *   <li>DRAFT 课程选课 → COURSE_NOT_PUBLISHED → <b>HTTP 400</b>（ErrorCode httpStatus=400）。</li>
 *   <li>重复选课 → enroll() 幂等优先检查 → 返回已存在记录（同 id，200）。</li>
 *   <li>非法状态跃迁 CANCELLED→COMPLETED → INVALID_STATUS_TRANSITION → <b>HTTP 400</b>。</li>
 *   <li>TEACHER 查看非自有课程学员 → assertCourseOwnership → NO_PERMISSION → <b>HTTP 403</b>。</li>
 * </ul>
 */
@DisplayName("B-3 链路2 选课流程")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EnrollmentFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();

    @AfterEach
    void cleanupEnrollment() {
        List<Long> users = new ArrayList<>(createdUserIds);
        users.add(7L); // p0-seed student
        for (Long u : users) {
            try {
                jdbc.update("DELETE FROM enrollment_histories WHERE enrollment_id IN " +
                        "(SELECT id FROM enrollments WHERE user_id = ?)", u);
            } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM enrollments WHERE user_id = ?", u); } catch (Exception ignored) {}
        }
        for (Long c : createdCourseIds) {
            try { jdbc.update("DELETE FROM courses WHERE id = ?", c); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        for (Long c : createdCategoryIds) {
            try { jdbc.update("DELETE FROM course_categories WHERE id = ?", c); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
        createdCourseIds.clear();
        createdCategoryIds.clear();
    }

    // --------- fixtures ---------

    private Long insertCategory() {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                        "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class, "enr-cat-" + System.nanoTime());
        createdCategoryIds.add(id);
        return id;
    }

    private Long insertUser(String role) {
        String username = role.toLowerCase() + "-" + System.nanoTime();
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class, username,
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv", "选课测试" + role, role);
        createdUserIds.add(id);
        return id;
    }

    private Long insertCourse(Long categoryId, Long teacherId, int status) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, true, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, "enr-course-" + System.nanoTime(), categoryId, teacherId, status);
        createdCourseIds.add(id);
        return id;
    }

    private Long insertEnrollment(Long userId, Long courseId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                        "VALUES (?, ?, 0, false, ?, now(), now()) RETURNING id",
                Long.class, courseId, userId, status);
    }

    private String studentTokenFor(Long userId, String username) {
        return jwtUtil.generateToken(userId, username, UserRole.STUDENT, null);
    }

    private String enrollBody(long courseId) {
        return "{\"courseId\":" + courseId + ",\"sourceChannel\":\"WEB\"}";
    }

    // --------- 1 ---------
    @Test
    @DisplayName("1·学生对 PUBLISHED 课程选课 → 200 + Enrollment 记录")
    void studentEnrollPublished_Returns200() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        MvcResult res = mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollBody(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.courseId").value(1))
                .andReturn();
        Number id = JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM enrollments WHERE id = ?", Long.class, id.longValue());
        assertEquals(1L, count, "应落库 1 条选课记录");
    }

    // --------- 2 ---------
    @Test
    @DisplayName("2·学生对 DRAFT 课程选课 → 400（COURSE_NOT_PUBLISHED）")
    void studentEnrollDraft_Returns400() throws Exception {
        Long cat = insertCategory();
        Long teacher = insertUser("TEACHER");
        Long draftCourse = insertCourse(cat, teacher, 0); // 0 = DRAFT
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollBody(draftCourse)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(6007));
    }

    // --------- 3 ---------
    @Test
    @DisplayName("3·重复选课 → 幂等返回已存在记录（同 id）")
    void duplicateEnroll_Idempotent() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        MvcResult r1 = mockMvc.perform(post("/api/enrollments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(1)))
                .andExpect(status().isOk()).andReturn();
        MvcResult r2 = mockMvc.perform(post("/api/enrollments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(1)))
                .andExpect(status().isOk()).andReturn();
        Number id1 = JsonPath.read(r1.getResponse().getContentAsString(), "$.data.id");
        Number id2 = JsonPath.read(r2.getResponse().getContentAsString(), "$.data.id");
        assertEquals(id1.longValue(), id2.longValue(), "幂等：两次选课返回同一 enrollment.id");
    }

    // --------- 4 ---------
    @Test
    @DisplayName("4·教师查看自己课程的选课学员列表 → 200")
    void teacherViewOwnCourseEnrollments_Returns200() throws Exception {
        // p0_teacher(id=6) 为 course 1 的 owner。注意：p0-seed 中 teacher 与 student 复用同一 bcrypt 哈希，
        // 实际明文口令为 student123（种子注释中的 p0teacher123 与实际哈希不符）。
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/enrollments/course/1?page=0&size=10")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    // --------- 5 ---------
    @Test
    @DisplayName("5·教师查看他人课程的选课学员列表 → 403")
    void teacherViewOthersCourseEnrollments_Returns403() throws Exception {
        Long cat = insertCategory();
        Long otherTeacher = insertUser("TEACHER");
        Long otherCourse = insertCourse(cat, otherTeacher, 4); // PUBLISHED，但 owner 是 otherTeacher
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/enrollments/course/" + otherCourse + "?page=0&size=10")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // --------- 6 ---------
    @Test
    @DisplayName("6·学生取消选课 → 状态变为 CANCELLED + 写 history")
    void studentCancelEnrollment_WritesHistory() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        MvcResult res = mockMvc.perform(post("/api/enrollments").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(enrollBody(2)))
                .andExpect(status().isOk()).andReturn();
        long enrollId = ((Number) JsonPath.read(res.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(delete("/api/enrollments/" + enrollId).header("Authorization", token))
                .andExpect(status().isOk());

        String dbStatus = jdbc.queryForObject(
                "SELECT enrollment_status FROM enrollments WHERE id = ?", String.class, enrollId);
        assertEquals("CANCELLED", dbStatus, "取消后状态必须为 CANCELLED");
        Long histCount = jdbc.queryForObject(
                "SELECT count(*) FROM enrollment_histories WHERE enrollment_id = ? AND new_status = 'CANCELLED'",
                Long.class, enrollId);
        assertTrue(histCount >= 1, "取消必须写入 CANCELLED 审计历史");
    }

    // --------- 7 ---------
    @Test
    @DisplayName("7·非法状态跃迁（CANCELLED → COMPLETED）→ 400")
    void illegalStatusTransition_Returns400() throws Exception {
        Long cat = insertCategory();
        Long teacher = insertUser("TEACHER");
        Long course = insertCourse(cat, teacher, 4);
        Long student = insertUser("STUDENT");
        Long enrollId = insertEnrollment(student, course, "CANCELLED");

        mockMvc.perform(put("/api/enrollments/" + enrollId)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentStatus\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(8004));
    }

    // --------- 8 ---------
    @Test
    @DisplayName("8·并发选课（5 线程）→ 最终只有 1 条成功")
    void concurrentEnroll_OnlyOneRecord() throws Exception {
        Long student = insertUser("STUDENT");
        String token = "Bearer " + studentTokenFor(student, "conc-student");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int code = mockMvc.perform(post("/api/enrollments").header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON).content(enrollBody(1)))
                            .andReturn().getResponse().getStatus();
                    if (code == 200) ok.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // 真实并发语义：唯一约束 uk_enroll_user_course 保证最终只有 1 条记录；
        // 至少 1 个请求拿到选课成功，落败请求因事务内唯一冲突（rollback-only）返回非 200——
        // 任务验收点为「最终只有 1 条成功」（数据完整性），故断言：≥1 成功 且 DB 恰好 1 条。
        assertTrue(ok.get() >= 1, "并发选课至少 1 个请求成功");
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM enrollments WHERE user_id = ? AND course_id = 1", Long.class, student);
        assertEquals(1L, count, "并发选课最终只允许 1 条选课记录");
    }
}

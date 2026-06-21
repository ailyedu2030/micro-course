package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;

import com.microcourse.util.RedisUtil;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 11-1 数据隔离深度加强 —— 隐私脱敏 + 跨角色边界 回归测试。
 *
 * <p>覆盖 5 个隔离修复点：</p>
 * <ol>
 *   <li>StudentDetailVO 手机/邮箱按角色脱敏（TEACHER/ACADEMIC 脱敏，ADMIN/本人完整）；</li>
 *   <li>UserController.getById 角色感知脱敏（本人完整）；</li>
 *   <li>选课排行不暴露他人真实 userId（仅本人回显）；</li>
 *   <li>批量导入原子性（任一行失败 → 整体不入库）；</li>
 *   <li>下架/归档课程对学生不可见（404），对课程 owner 教师可见（200）。</li>
 * </ol>
 *
 * <p>测试范式沿用 {@link MissingEndpointsTest}：仅依赖 admin/p0_teacher/student 种子账号，
 * 以隔离数据（jdbc 插入 + finally 按 ID 清理）保证零环境耦合、对既有 PASS 测试零退化。</p>
 *
 * <p>种子账号（/sql/p0-seed.sql）：admin/admin123(ADMIN,1)、p0_teacher/p0teacher123(TEACHER,6)、
 * student/student123(STUDENT,7)。</p>
 */
@DisplayName("Round 11-1 数据隔离 · 隐私脱敏 + 跨角色边界")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DataIsolationTest extends BaseIntegrationTest {

    /** student123 与 p0-seed 同算法 bcrypt 口令，供隔离学员账号占位（无需登录）。 */
    private static final String BCRYPT_STUDENT123 =
            "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK";

    private static final String RAW_PHONE = "13812345678";
    private static final String RAW_EMAIL = "isostudent@example.com";
    private static final String MASKED_PHONE = "138****5678";
    private static final String MASKED_EMAIL = "i***@example.com";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 清除本类所用种子账号的登录失败计数（login:lock:*）。
     * <p>历史误用错误口令的运行可能残留失败计数（30min TTL），借应用同一 Redis 连接清零，
     * 保证 p0_teacher / student 正确口令登录不被 1006（账号锁定）误拦，测试自治、可重复。</p>
     */
    @BeforeEach
    void clearLoginLocks() {
        redisUtil.clearLoginFailure("p0_teacher");
        redisUtil.clearLoginFailure("student");
        redisUtil.clearLoginFailure("admin");
    }

    // ───────────────────────── 任务 1/2 · StudentDetailVO 脱敏 ─────────────────────────

    @Test
    @DisplayName("[隔离] 教师查看学员详情 → 手机号脱敏 138****5678")
    void studentPhoneShouldBeMaskedForTeacher() throws Exception {
        Long studentId = insertIsolatedStudent();
        try {
            String body = mockMvc.perform(get("/api/enrollments/student-detail/" + studentId)
                            .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String phone = JsonPath.read(body, "$.data.phone");
            assertEquals(MASKED_PHONE, phone, "教师视角学员手机号必须脱敏");
        } finally {
            deleteUser(studentId);
        }
    }

    @Test
    @DisplayName("[隔离] 教师查看学员详情 → 邮箱脱敏 i***@example.com")
    void studentEmailShouldBeMaskedForTeacher() throws Exception {
        Long studentId = insertIsolatedStudent();
        try {
            String body = mockMvc.perform(get("/api/enrollments/student-detail/" + studentId)
                            .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String email = JsonPath.read(body, "$.data.email");
            assertEquals(MASKED_EMAIL, email, "教师视角学员邮箱必须脱敏");
        } finally {
            deleteUser(studentId);
        }
    }

    @Test
    @DisplayName("[隔离] 管理员查看学员详情 → 手机/邮箱完整")
    void studentDetailShouldBeCompleteForAdmin() throws Exception {
        Long studentId = insertIsolatedStudent();
        try {
            String body = mockMvc.perform(get("/api/enrollments/student-detail/" + studentId)
                            .header("Authorization", bearerAdmin()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(RAW_PHONE, JsonPath.read(body, "$.data.phone"), "管理员应看到完整手机号");
            assertEquals(RAW_EMAIL, JsonPath.read(body, "$.data.email"), "管理员应看到完整邮箱");
        } finally {
            deleteUser(studentId);
        }
    }

    @Test
    @DisplayName("[隔离] 本人查看自己用户详情 → 手机/邮箱完整")
    void studentCanSeeOwnDetail() throws Exception {
        // student(id=7) 种子无敏感信息，临时补齐后由本人 token 读取，验证"本人完整"路径，finally 复原。
        jdbc.update("UPDATE users SET phone = ?, email = ? WHERE id = 7", RAW_PHONE, RAW_EMAIL);
        try {
            String body = mockMvc.perform(get("/api/users/7")
                            .header("Authorization", "Bearer " + loginAs("student", "student123")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertEquals(RAW_PHONE, JsonPath.read(body, "$.data.phone"), "本人应看到完整手机号");
            assertEquals(RAW_EMAIL, JsonPath.read(body, "$.data.email"), "本人应看到完整邮箱");
        } finally {
            jdbc.update("UPDATE users SET phone = NULL, email = NULL WHERE id = 7");
        }
    }

    // ───────────────────────── 任务 3 · 排行不暴露 userId ─────────────────────────

    @Test
    @DisplayName("[隔离] 选课排行对非本人不暴露真实 userId（置 null）")
    @SuppressWarnings("unchecked")
    void rankingShouldNotExposeUserId() throws Exception {
        Long courseId = insertIsolatedCourse(4); // PUBLISHED
        try {
            jdbc.update("INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status) "
                    + "VALUES(?, 7, 80.0, FALSE, 'ENROLLED')", courseId);
            // admin（非该课任何选课学员）请求 → 榜单中所有成员对其而言均非本人，userId 必须为 null。
            String body = mockMvc.perform(get("/api/enrollments/course/" + courseId + "/ranking")
                            .header("Authorization", bearerAdmin()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> ranking = JsonPath.read(body, "$.data");
            assertFalse(ranking.isEmpty(), "排行榜应至少包含 1 条选课记录");
            for (Map<String, Object> row : ranking) {
                assertNull(row.get("userId"),
                        "排行榜对非本人不得暴露真实 userId，实际=" + row.get("userId"));
            }
        } finally {
            jdbc.update("DELETE FROM enrollments WHERE course_id = ?", courseId);
            deleteCourse(courseId);
        }
    }

    // ───────────────────────── 任务 4 · 批量导入原子性 ─────────────────────────

    @Test
    @DisplayName("[隔离] 批量导入含错误行 → 整体回滚，合法行不入库")
    void batchImportShouldRollbackOnError() throws Exception {
        // 预生成 fixture（fixtures/batch-import-rollback.xlsx）：
        //   行1 合法 username=iso_batch_rollback_fixed；行2 username=admin（已存在 → 错误行）。
        //   因原子性保证合法行永不入库，固定 username 可重复执行而无残留。
        final String okUsername = "iso_batch_rollback_fixed";
        byte[] bytes;
        try (InputStream in = new ClassPathResource("fixtures/batch-import-rollback.xlsx").getInputStream()) {
            bytes = in.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);

        try {
            String body = mockMvc.perform(multipart("/api/users/batch").file(file)
                            .header("Authorization", bearerAdmin()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            int successCount = JsonPath.read(body, "$.data.successCount");
            int failCount = JsonPath.read(body, "$.data.failCount");
            assertEquals(0, successCount, "存在错误行时不得有任何成功导入（原子性）");
            assertTrue(failCount >= 1, "应至少记录 1 条失败行");

            Integer cnt = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, okUsername);
            assertEquals(0, cnt, "合法行必须随错误整体回滚，不得入库");
        } finally {
            jdbc.update("DELETE FROM users WHERE username = ?", okUsername);
        }
    }

    // ───────────────────────── 任务 5 · 下架课程跨角色边界 ─────────────────────────

    @Test
    @DisplayName("[隔离] 下架课程对学生不可见 → 404")
    void closedCourseShouldBe404ForStudent() throws Exception {
        Long courseId = insertIsolatedCourse(5); // CLOSED
        try {
            mockMvc.perform(get("/api/courses/" + courseId)
                            .header("Authorization", "Bearer " + loginAs("student", "student123")))
                    .andExpect(status().isNotFound());
        } finally {
            deleteCourse(courseId);
        }
    }

    @Test
    @DisplayName("[隔离] 课程 owner 教师可见自己下架的课程 → 200")
    void teacherCanViewOwnClosedCourse() throws Exception {
        Long courseId = insertIsolatedCourse(5); // CLOSED, teacher_id=6
        try {
            mockMvc.perform(get("/api/courses/" + courseId)
                            .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                    .andExpect(status().isOk());
        } finally {
            deleteCourse(courseId);
        }
    }

    // ───────────────────────── 测试夹具辅助 ─────────────────────────

    /** 插入一名带完整手机/邮箱的隔离学员（仅供被查看，无需登录），返回其 id。 */
    private Long insertIsolatedStudent() {
        return jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, "
                        + "email, phone, created_at, updated_at) "
                        + "VALUES(?, ?, '隔离学员', 'STUDENT', 1, FALSE, ?, ?, NOW(), NOW()) RETURNING id",
                Long.class, "iso_stu_" + System.nanoTime(), BCRYPT_STUDENT123, RAW_EMAIL, RAW_PHONE);
    }

    /** 插入一门归属 p0_teacher(id=6) 的隔离课程（category_id=1 来自 p0-seed），返回其 id。 */
    private Long insertIsolatedCourse(int status) {
        return jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, "
                        + "course_type, version, created_at, updated_at) "
                        + "VALUES(?, 1, 6, ?, TRUE, NULL, 'VIDEO', 0, NOW(), NOW()) RETURNING id",
                Long.class, "iso-course-" + System.nanoTime(), status);
    }

    private void deleteUser(Long userId) {
        try { jdbc.update("DELETE FROM users WHERE id = ?", userId); } catch (Exception ignored) {}
    }

    private void deleteCourse(Long courseId) {
        try { jdbc.update("DELETE FROM course_review_logs WHERE course_id = ?", courseId); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM courses WHERE id = ?", courseId); } catch (Exception ignored) {}
    }
}

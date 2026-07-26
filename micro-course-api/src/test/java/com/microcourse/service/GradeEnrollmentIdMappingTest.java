package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 6 P0: GradeVO enrollmentId 批量映射集成测试。
 * <p>
 * 验证 batchConvertToVO 中的 enrollmentId 批量填充逻辑：
 * <ol>
 *   <li>page() 响应每个 GradeVO 均含非空 enrollmentId</li>
 *   <li>同一学生多课程时每个 enrollmentId 正确匹配各自课程</li>
 *   <li>教师可用 page 返回的 enrollmentId 直接完成 teacherGrade 提交（端到端）</li>
 * </ol>
 * <p>
 * 种子数据：p0-seed.sql（teacher=6 / student=7 / 课程 1..4 已发布免费，教师均为 6）。
 */
@DisplayName("P0 GradeVO enrollmentId 批量映射")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestMethodOrder(MethodOrderer.MethodName.class)
class GradeEnrollmentIdMappingTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtUtil jwtUtil;

    private static final Long TEACHER_ID = 6L;
    private static final Long STUDENT_ID = 7L;

    @BeforeEach
    void cleanUp() {
        // 按依赖逆序清理，防止 FK 约束阻止删除
        jdbc.update("DELETE FROM grades WHERE user_id = ?", STUDENT_ID);
        jdbc.update("DELETE FROM enrollment_histories WHERE enrollment_id IN "
                + "(SELECT id FROM enrollments WHERE user_id = ?)", STUDENT_ID);
        jdbc.update("DELETE FROM enrollments WHERE user_id = ?", STUDENT_ID);
    }

    // ======================================================================
    // 测试 1: page() 返回的 GradeVO 均应含非空 enrollmentId
    // ======================================================================

    @Test
    @DisplayName("[1] page() 返回的 GradeVO 均应含非空 enrollmentId")
    void pageReturnsEnrollmentId() throws Exception {
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);

        // 学生报名课程 1
        Long enrollmentId1 = enroll(studentToken, 1L);

        // 创建一条 grade 记录（模拟教师已录入成绩）
        insertGrade(1L);

        // 查询成绩列表 — 验证 enrollmentId 不为空且匹配
        MvcResult result = mockMvc.perform(get("/api/grades")
                        .param("courseId", "1")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].enrollmentId").exists())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        Number eid = JsonPath.read(json, "$.data.items[0].enrollmentId");
        assertNotNull(eid, "enrollmentId must not be null in page response");
        assertEquals(enrollmentId1.longValue(), eid.longValue(),
                "enrollmentId must match the actual enrollment record");
    }

    // ======================================================================
    // 测试 2: 同一学生多课程 — 每个 enrollmentId 应正确匹配各自课程
    // ======================================================================

    @Test
    @DisplayName("[2] 同一学生多课程时每个 enrollmentId 正确匹配")
    void sameStudentMultiCourseCorrectEnrollmentIds() throws Exception {
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);

        // 学生报名课程 1 和 2
        Long enrollmentId1 = enroll(studentToken, 1L);
        Long enrollmentId2 = enroll(studentToken, 2L);
        assertNotEquals(enrollmentId1, enrollmentId2, "Two course enrollments must have different IDs");

        // 创建课程 1 和 2 的成绩记录
        insertGrade(1L);
        insertGrade(2L);

        // 查询成绩列表（不限课程，教师 6 可看到所有自己课程的成绩）
        MvcResult result = mockMvc.perform(get("/api/grades")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();

        // 逐一提取每行 (courseId, enrollmentId) 建立映射
        Integer length = JsonPath.read(json, "$.data.items.length()");
        assertTrue(length >= 2, "Should have at least 2 grade records");

        Map<Long, Long> courseToEnrollment = new HashMap<>();
        for (int i = 0; i < length; i++) {
            Number cId = JsonPath.read(json, "$.data.items[" + i + "].courseId");
            Number eId = JsonPath.read(json, "$.data.items[" + i + "].enrollmentId");
            assertNotNull(eId, "enrollmentId must not be null at index " + i);
            courseToEnrollment.put(cId.longValue(), eId.longValue());
        }

        // 验证课程 1 → enrollmentId1, 课程 2 → enrollmentId2
        assertEquals(enrollmentId1, courseToEnrollment.get(1L),
                "Course 1 grade should map to its own enrollment");
        assertEquals(enrollmentId2, courseToEnrollment.get(2L),
                "Course 2 grade should map to its own enrollment");
    }

    // ======================================================================
    // 测试 3: 端到端 — page → enrollmentId → teacherGrade 正常提交
    // ======================================================================

    @Test
    @DisplayName("[3] 教师可用 page 返回的 enrollmentId 直接完成 teacherGrade 提交")
    void pageEnrollmentIdUsedByTeacherGrade() throws Exception {
        String studentToken = jwtUtil.generateToken(STUDENT_ID, "student", UserRole.STUDENT, 1L);
        String teacherToken = jwtUtil.generateToken(TEACHER_ID, "p0_teacher", UserRole.TEACHER, 1L);

        // 学生报名课程 1
        enroll(studentToken, 1L);

        // 创建一个 grade 记录（分数为空 — 待批改）
        jdbc.update("INSERT INTO grades (course_id, user_id, graded_by, graded_at, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, now(), now(), now(), 0)",
                1L, STUDENT_ID, TEACHER_ID);

        // 步骤 1: page() 获取 enrollmentId
        MvcResult pageResult = mockMvc.perform(get("/api/grades")
                        .param("courseId", "1")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();

        String pageJson = pageResult.getResponse().getContentAsString();
        Number enrollmentId = JsonPath.read(pageJson, "$.data.items[0].enrollmentId");
        assertNotNull(enrollmentId, "enrollmentId from page must not be null");

        // 步骤 2: 用该 enrollmentId 调用 teacherGrade 批改成绩
        String body = "{\"enrollmentId\":" + enrollmentId.longValue() + ",\"score\":88,\"comment\":\"端到端验证\"}";
        mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(88))
                .andExpect(jsonPath("$.data.enrollmentId").value(enrollmentId.longValue()));
    }

    // ======================================================================
    // 辅助方法
    // ======================================================================

    /** 学生报名某课程，返回 enrollmentId */
    private Long enroll(String studentToken, Long courseId) throws Exception {
        String body = "{\"courseId\":" + courseId + ",\"sourceChannel\":\"direct\"}";
        MvcResult result = mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Object id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return ((Number) id).longValue();
    }

    /** 插入一条 grade 记录（课程 teacher_id 必须 = 6） */
    private void insertGrade(Long courseId) {
        jdbc.update("INSERT INTO grades (course_id, user_id, score, total_score, passed, "
                        + "graded_by, graded_at, created_at, updated_at, version) "
                        + "VALUES (?, ?, 85, 100, true, ?, now(), now(), now(), 0)",
                courseId, STUDENT_ID, TEACHER_ID);
    }
}

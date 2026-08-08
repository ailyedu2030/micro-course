package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G3-P0-5: 播放器 video_progress 上报端点测试。
 *
 * <p>PUT /api/learning-progress/{courseId}/sections/{sectionId}/video-progress</p>
 *
 * <ul>
 *   <li>学生上报 played/total → 服务端计算 video_progress 写入 learning_progress
 *       （SKIP_IF_KNOWN 服务端读取的数据源；纯 PPT/HTML 场景此前恒 null → SKIP 永不命中）</li>
 *   <li>播放完成 → 100（max 1.0）</li>
 *   <li>仅 STUDENT 可调用（教师 403）</li>
 *   <li>未选课学生 → NOT_ENROLLED 403</li>
 * </ul>
 */
@DisplayName("G3-P0-5 video_progress 上报（SKIP_IF_KNOWN 数据源）")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LearningProgressControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private String studentToken() throws Exception {
        return "Bearer " + loginAs("student", "student123");
    }

    @BeforeEach
    void enrollStudent() {
        jdbc.update("INSERT INTO enrollments (user_id, course_id, enrollment_status, enrolled_at, updated_at, version) " +
                "VALUES (7, 1, 'APPROVED', NOW(), NOW(), 0) " +
                "ON CONFLICT (user_id, course_id) WHERE deleted_at IS NULL DO NOTHING");
    }

    @AfterEach
    void cleanup() {
        try { jdbc.update("DELETE FROM learning_progress WHERE user_id = 7"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7 AND course_id = 1"); } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("学生上报 played=40s/total=50s → video_progress=80 写入 learning_progress")
    void reportVideoProgress_WritesComputedProgress() throws Exception {
        String token = studentToken();
        mockMvc.perform(put("/api/learning-progress/1/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":40,\"totalSeconds\":50}"))
                .andExpect(status().isOk());

        Integer vp = jdbc.queryForObject(
                "SELECT video_progress FROM learning_progress WHERE user_id = 7 AND course_id = 1 AND lesson_id = 99",
                Integer.class);
        assertEquals(80, vp, "服务端计算 video_progress = round(40/50*100) = 80");
    }

    @Test
    @DisplayName("播放完成 played=total → video_progress=100（max 1.0）")
    void reportVideoProgress_CapsAt100() throws Exception {
        String token = studentToken();
        mockMvc.perform(put("/api/learning-progress/1/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":50,\"totalSeconds\":50}"))
                .andExpect(status().isOk());

        Integer vp = jdbc.queryForObject(
                "SELECT video_progress FROM learning_progress WHERE user_id = 7 AND course_id = 1 AND lesson_id = 99",
                Integer.class);
        assertEquals(100, vp, "播放完成 → 100（max 1.0）");
    }

    @Test
    @DisplayName("重复上报（翻页）不产生重复记录，video_progress 持续更新")
    void reportVideoProgress_UpdatesExistingRow() throws Exception {
        String token = studentToken();
        // 第一次翻页上报 20%
        mockMvc.perform(put("/api/learning-progress/1/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":10,\"totalSeconds\":50}"))
                .andExpect(status().isOk());
        // 听完后上报 100%
        mockMvc.perform(put("/api/learning-progress/1/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":50,\"totalSeconds\":50}"))
                .andExpect(status().isOk());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM learning_progress WHERE user_id = 7 AND course_id = 1 AND lesson_id = 99",
                Integer.class);
        assertEquals(1, count, "重复上报必须幂等更新，不得产生重复记录");
        Integer vp = jdbc.queryForObject(
                "SELECT video_progress FROM learning_progress WHERE user_id = 7 AND course_id = 1 AND lesson_id = 99",
                Integer.class);
        assertEquals(100, vp, "最后一次上报覆盖为 100");
    }

    @Test
    @DisplayName("教师上报 → 403（仅 STUDENT 可调用）")
    void reportVideoProgress_TeacherForbidden() throws Exception {
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(put("/api/learning-progress/1/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":10,\"totalSeconds\":20}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未选课学生上报 → NOT_ENROLLED 403")
    void reportVideoProgress_NotEnrolled() throws Exception {
        String token = studentToken();
        // student(7) 只选了 course 1，course 2 未选课 → 拒绝
        mockMvc.perform(put("/api/learning-progress/2/sections/99/video-progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playedSeconds\":10,\"totalSeconds\":20}"))
                .andExpect(status().isForbidden());
    }
}

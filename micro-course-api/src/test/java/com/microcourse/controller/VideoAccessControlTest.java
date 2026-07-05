package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 8-1 · 视频访问选课控制集成测试（商业致命 P0 修复回归）。
 *
 * <p>验证"未选课也能看视频"缺陷已闭合：视频元数据 / 签名 / 播放 / HLS 流四条链路
 * 对<b>未选课学生</b>一律 403 NOT_ENROLLED(8005)，对已选课学生 / 教师 / 管理员零退化放行。</p>
 *
 * <p>种子（/sql/p0-seed.sql）：course 1/2（teacher=6）、chapter 1（课程1）/5（课程2）、
 * student(id=7,'student123')、p0_teacher(id=6,'student123')、admin(id=1,'admin123')。
 * student(id=7) 默认<b>未</b>对任何课程选课，故用 course 2 构造"未选课"场景。</p>
 */
@DisplayName("Round 8-1 视频访问选课控制")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VideoAccessControlTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdVideoIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 清理选课（student id=7）+ 本类创建的视频，保证用例相互隔离
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7"); } catch (Exception ignored) {}
        for (Long v : createdVideoIds) {
            try { jdbc.update("DELETE FROM videos WHERE id = ?", v); } catch (Exception ignored) {}
        }
        createdVideoIds.clear();
    }

    /** 插入一条可播放视频（status=2 COMPLETED，带 m3u8）。 */
    private long insertVideo(long courseId, long chapterId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO videos(course_id, chapter_id, title, status, m3u8_url, progress, sort_order, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 2, '/api/videos/stream/test/index.m3u8', 100, 0, 0, now(), now()) RETURNING id",
                Long.class, courseId, chapterId, "r8-vid-" + System.nanoTime());
        createdVideoIds.add(id);
        return id;
    }

    /** student(id=7) 选课指定课程（status='ENROLLED'，与业务实际写入值一致）。 */
    private void enrollStudent7(long courseId) {
        jdbc.update(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                        "SELECT ?, 7, 0, false, 'APPROVED', now(), now() " +
                        "WHERE NOT EXISTS (SELECT 1 FROM enrollments WHERE user_id = 7 AND course_id = ? AND deleted_at IS NULL)",
                courseId, courseId);
    }

    private String studentToken() throws Exception {
        return "Bearer " + loginAs("student", "student123");
    }

    private String teacherToken() throws Exception {
        return "Bearer " + loginAs("p0_teacher", "student123");
    }

    // --------- 1 · 未选课学生 → 视频元数据 403 ---------
    @Test
    @DisplayName("1·未选课学生访问视频元数据 → 403 NOT_ENROLLED")
    void studentNotEnrolledCannotAccessVideo() throws Exception {
        long videoId = insertVideo(2, 5); // course 2，student 7 未选
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", studentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(8005));
    }

    // --------- 2 · 已选课学生 → 视频元数据 200 ---------
    @Test
    @DisplayName("2·已选课学生访问视频元数据 → 200（体验零退化）")
    void enrolledStudentCanAccessVideo() throws Exception {
        long videoId = insertVideo(1, 1); // course 1
        enrollStudent7(1);                // 已选课
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", studentToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    // --------- 3 · 教师 → 自己课程视频 200 ---------
    @Test
    @DisplayName("3·教师访问自己课程视频 → 200（不误伤教师）")
    void teacherCanAccessOwnCourseVideo() throws Exception {
        long videoId = insertVideo(1, 1); // course 1，owner = p0_teacher(id=6)
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // --------- 4 · 管理员 → 任意视频 200 ---------
    @Test
    @DisplayName("4·管理员访问任意视频 → 200（不误伤管理员）")
    void adminCanAccessAnyVideo() throws Exception {
        long videoId = insertVideo(2, 5); // course 2，admin 未选课但应放行
        mockMvc.perform(get("/api/videos/" + videoId).header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // --------- 5 · 未选课学生 → 签名 403 ---------
    @Test
    @DisplayName("5·未选课学生获取播放签名 → 403 NOT_ENROLLED")
    void studentNotEnrolledCannotGetSign() throws Exception {
        long videoId = insertVideo(2, 5); // course 2，未选
        mockMvc.perform(get("/api/videos/" + videoId + "/sign").header("Authorization", studentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(8005));
    }

    // --------- 6 · 未选课学生 → 播放 403（先于签名校验） ---------
    @Test
    @DisplayName("6·未选课学生播放视频 → 403 NOT_ENROLLED（先于签名校验）")
    void studentNotEnrolledCannotPlayVideo() throws Exception {
        long videoId = insertVideo(2, 5); // course 2，未选
        // 选课校验位于签名校验之前：即便携带任意 sign，未选课也直接 403 NOT_ENROLLED
        mockMvc.perform(get("/api/videos/" + videoId + "/play")
                        .param("sign", "whatever")
                        .header("Authorization", studentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(8005));
    }

    // --------- 7 · 未选课学生 → HLS 流 403（先于文件处理） ---------
    @Test
    @DisplayName("7·未选课学生访问 HLS 流 → 403 NOT_ENROLLED（先于文件处理）")
    void studentNotEnrolledCannotStreamVideo() throws Exception {
        long videoId = insertVideo(2, 5); // course 2，未选
        // stream 端点选课校验位于路径/文件处理之前：未选课直接 403，不泄露文件是否存在
        mockMvc.perform(get("/api/video-stream/2/" + videoId + "/index.m3u8")
                        .header("Authorization", studentToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(8005));
    }
}

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase B-3 · 链路 3 · 视频学习集成测试（6 用例）。
 *
 * <p>断言对齐实际实现（VideoController / VideoSignUtil / LearningProgressServiceImpl）：</p>
 * <ul>
 *   <li>{@code GET /{id}/sign} 仅 isAuthenticated —— 选课校验为<b>未来加固项</b>（P0-3），当前已认证学生即可获签。</li>
 *   <li>{@code GET /{id}/play} 成功返回 <b>302 重定向</b>到 m3u8（非 200）。</li>
 *   <li>转码未完成（无 m3u8_url）→ VIDEO_TRANSCODE_FAILED → 非可播放重定向（断言 != 302）。</li>
 *   <li>视频进度 POST 复用并发安全的 LearningProgressService（Phase B-1 @Version）。</li>
 * </ul>
 */
@DisplayName("B-3 链路3 视频学习")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class VideoLearningFlowIntegrationTest extends BaseIntegrationTest {

    private final JdbcTemplate jdbc;
    private final JwtUtil jwtUtil;

    VideoLearningFlowIntegrationTest(JdbcTemplate jdbc, JwtUtil jwtUtil) {
        this.jdbc = jdbc;
        this.jwtUtil = jwtUtil;
    }

    private final List<Long> createdVideoIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanupVideo() {
        // learning_progress.lesson_id → videos(ON DELETE CASCADE)，但显式先清进度更稳妥
        List<Long> users = new ArrayList<>(createdUserIds);
        users.add(7L);
        for (Long u : users) {
            try { jdbc.update("DELETE FROM learning_progress WHERE user_id = ?", u); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM enrollments WHERE user_id = ?", u); } catch (Exception ignored) {}
        }
        for (Long v : createdVideoIds) {
            try { jdbc.update("DELETE FROM videos WHERE id = ?", v); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        createdVideoIds.clear();
        createdUserIds.clear();
    }

    /** 插入视频：status 2=COMPLETED 可播放（带 m3u8）；1=TRANSCODING 不可播放（m3u8=null） */
    private Long insertVideo(int status, String m3u8Url) {
        Long id = jdbc.queryForObject(
                "INSERT INTO videos(course_id, chapter_id, title, status, m3u8_url, progress, sort_order, version, created_at, updated_at) " +
                        "VALUES (1, 1, ?, ?, ?, 100, 0, 0, now(), now()) RETURNING id",
                Long.class, "vid-" + System.nanoTime(), status, m3u8Url);
        createdVideoIds.add(id);
        return id;
    }

    private Long insertStudent() {
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, '$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv', '视频测试学生', 'STUDENT', 1, false, now(), now()) RETURNING id",
                Long.class, "vid-student-" + System.nanoTime());
        createdUserIds.add(id);
        return id;
    }

    /**
     * Round 8-1：让 student(id=7) 对 course 1 选课，使"合法已选课学生"可正常观看视频。
     * 幂等（WHERE NOT EXISTS），由 {@code @AfterEach} 清理。
     */
    private void enrollStudent7InCourse1() {
        jdbc.update(
                "INSERT INTO enrollments(course_id, user_id, progress, completed, enrollment_status, enrolled_at, updated_at) " +
                        "SELECT 1, 7, 0, false, 'ENROLLED', now(), now() " +
                        "WHERE NOT EXISTS (SELECT 1 FROM enrollments WHERE user_id = 7 AND course_id = 1 AND deleted_at IS NULL)");
    }

    private String fetchSign(String bearer, long videoId) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/videos/" + videoId + "/sign")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.data");
    }

    // --------- 1 ---------
    @Test
    @DisplayName("1·学生获取视频签名 → 200 + sign")
    void studentGetVideoSign_Returns200() throws Exception {
        long videoId = insertVideo(2, "/api/videos/stream/test/index.m3u8");
        enrollStudent7InCourse1(); // Round 8-1：合法已选课学生，体验零退化
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/videos/" + videoId + "/sign").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString());
    }

    // --------- 2 ---------
    @Test
    @DisplayName("2·学生上报视频进度 → 200 + progress 更新")
    void studentReportVideoProgress_Returns200() throws Exception {
        long videoId = insertVideo(2, "/api/videos/stream/test/index.m3u8");
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(post("/api/videos/" + videoId + "/progress")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoProgress\":50,\"videoPosition\":30,\"totalWatchTime\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.videoProgress").value(50));

        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM learning_progress WHERE user_id = 7 AND course_id = 1", Long.class);
        assertTrue(count >= 1, "上报进度后应落库 learning_progress 记录");
    }

    // --------- 3 ---------
    @Test
    @DisplayName("3·未选课学生获取视频签名 → 403 NOT_ENROLLED（Round 8-1 选课加固已落地）")
    void unenrolledStudentGetSign_Forbidden() throws Exception {
        long videoId = insertVideo(2, "/api/videos/stream/test/index.m3u8");
        Long student = insertStudent(); // 未对任何课程选课
        String token = "Bearer " + jwtUtil.generateToken(student, "unenrolled", UserRole.STUDENT, null);
        // Round 8-1：/sign 现已强制选课校验。未选课学生不得获签 → 403 NOT_ENROLLED(8005)。
        // 这正是"未选课也能看视频"商业致命缺陷的修复点（此前仅注释/未来项，现已真正落地）。
        mockMvc.perform(get("/api/videos/" + videoId + "/sign").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(8005));
    }

    // --------- 4 ---------
    @Test
    @DisplayName("4·并发上报同一视频进度 → 全部成功（并发安全，无 5xx）")
    void concurrentReportProgress_AllSucceed() throws Exception {
        long videoId = insertVideo(2, "/api/videos/stream/test/index.m3u8");
        String token = "Bearer " + loginAs("student", "student123");

        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            final int p = (i + 1) * 10;
            pool.submit(() -> {
                try {
                    start.await();
                    int code = mockMvc.perform(post("/api/videos/" + videoId + "/progress")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"videoProgress\":" + p + ",\"totalWatchTime\":" + p + "}"))
                            .andReturn().getResponse().getStatus();
                    if (code == 200) ok.incrementAndGet();
                } catch (Exception ignored) {}
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(threads, ok.get(), "并发上报全部 200（无数据竞争导致的 5xx）");
    }

    // --------- 5 ---------
    @Test
    @DisplayName("5·转码中视频不能播放（play 不返回可播放 302）")
    void transcodingVideoCannotPlay() throws Exception {
        long videoId = insertVideo(1, null); // TRANSCODING，无 m3u8_url
        enrollStudent7InCourse1(); // Round 8-1：合法已选课学生
        String token = "Bearer " + loginAs("student", "student123");
        String sign = fetchSign(token, videoId);

        int code = mockMvc.perform(get("/api/videos/" + videoId + "/play")
                        .header("Authorization", token)
                        .param("sign", sign))
                .andReturn().getResponse().getStatus();
        // 转码未完成 → VIDEO_TRANSCODE_FAILED，绝不返回可播放重定向(302)
        assertNotEquals(302, code, "转码未完成的视频不应返回可播放重定向");
        assertTrue(code >= 400, "转码未完成的视频播放应被拒绝(>=400)，实际=" + code);
    }

    // --------- 6 ---------
    @Test
    @DisplayName("6·转码完成视频可以播放 → 302 重定向到 m3u8")
    void completedVideoCanPlay_Redirects302() throws Exception {
        long videoId = insertVideo(2, "/api/videos/stream/test/index.m3u8"); // COMPLETED + m3u8
        enrollStudent7InCourse1(); // Round 8-1：合法已选课学生
        String token = "Bearer " + loginAs("student", "student123");
        String sign = fetchSign(token, videoId);

        mockMvc.perform(get("/api/videos/" + videoId + "/play")
                        .header("Authorization", token)
                        .param("sign", sign))
                .andExpect(status().isFound()) // 302
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", "/api/videos/stream/test/index.m3u8"));
    }
}

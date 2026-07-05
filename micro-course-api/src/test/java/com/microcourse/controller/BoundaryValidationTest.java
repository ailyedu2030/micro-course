package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import com.microcourse.exception.ErrorCode;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 9-3 · 边界 case + 输入验证（5 用例）。
 *
 * <p>验证"UX 优先"下的边界友好响应：超长/负值输入返回 400 友好提示（非 500），
 * 重复提交不报错（静默成功）。所有断言对齐实际实现：</p>
 * <ul>
 *   <li>答案超长 / duration 负值 → {@code @Valid} 在进入 Service 前拦截 → HTTP 400。</li>
 *   <li>videoProgress 负值 → {@code @PositiveOrZero} → HTTP 400。</li>
 *   <li>空封面文件 → VideoController/VideoServiceImpl 双层防御 → HTTP 400。</li>
 *   <li>重复提交：后端每次计算新 attemptNo，二次提交仍返回 200（不报错、不抛 500）；
 *       前端 submitting 防抖进一步阻止双击触达后端。</li>
 * </ul>
 */
@DisplayName("Round 9-3 边界 case + 输入验证")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BoundaryValidationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdQuestionIds = new ArrayList<>();
    private final List<Long> createdExerciseIds = new ArrayList<>();

    @BeforeEach
    void enrollStudent() {
        // 显式清理+重置(根因修复:防止跨 test class 状态污染导致 BackendP0FixesTest.enrollShouldAtomicallyIncrementStudentCount flaky)
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7 AND course_id = 1"); } catch (Exception ignored) {}
        try { jdbc.update("UPDATE courses SET student_count = 0 WHERE id = 1"); } catch (Exception ignored) {}
        jdbc.update("INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, enrolled_at, updated_at) " +
                "VALUES (7, 1, 'APPROVED', 'WEB', now(), now()) ON CONFLICT DO NOTHING");
        // P1C-024 视频进度阈值检查 — 创建一条课程级学习进度（completed=true）
        jdbc.update("INSERT INTO learning_progress (user_id, course_id, video_progress, completed, total_watch_time, created_at, updated_at) " +
                "VALUES (7, 1, 100.0, true, 300, now(), now()) ON CONFLICT DO NOTHING");
    }

    @AfterEach
    void cleanupBoundary() {
        // ★ 根因修复: 跨 class 状态污染源 — 必须清理 enrollment,否则污染 BackendP0FixesTest 等
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7 AND course_id = 1"); } catch (Exception ignored) {}
        try { jdbc.update("UPDATE courses SET student_count = 0 WHERE id = 1"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM learning_progress WHERE user_id = 7 AND course_id = 1"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM exercise_records WHERE user_id = 7"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM grades WHERE user_id = 7"); } catch (Exception ignored) {}
        try { jdbc.update("DELETE FROM wrong_questions WHERE user_id = 7"); } catch (Exception ignored) {}
        for (Long e : createdExerciseIds) {
            try { jdbc.update("DELETE FROM exercises WHERE id = ?", e); } catch (Exception ignored) {}
        }
        for (Long q : createdQuestionIds) {
            try { jdbc.update("DELETE FROM questions WHERE id = ?", q); } catch (Exception ignored) {}
        }
        createdQuestionIds.clear();
        createdExerciseIds.clear();
    }

    // --------- fixtures（对齐 ExerciseFlowIntegrationTest）---------

    private Long insertQuestion(String type, String answer) {
        Long id = jdbc.queryForObject(
                "INSERT INTO questions(course_id, teacher_id, question_type, content, answer, difficulty, version, status, created_at, updated_at) " +
                        "VALUES (1, 6, ?, ?, ?, 1, 0, 1, now(), now()) RETURNING id",
                Long.class, type, "题目-" + System.nanoTime(), answer);
        createdQuestionIds.add(id);
        return id;
    }

    private Long insertExercise(int passScore, int maxAttempts, int totalScore, int questionCount) {
        Long id = jdbc.queryForObject(
                "INSERT INTO exercises(course_id, title, pass_score, time_limit, max_attempts, total_score, question_count, version, created_at, updated_at) " +
                        "VALUES (1, ?, ?, 0, ?, ?, ?, 0, now(), now()) RETURNING id",
                Long.class, "练习-" + System.nanoTime(), passScore, maxAttempts, totalScore, questionCount);
        createdExerciseIds.add(id);
        return id;
    }

    private void linkQuestion(Long exerciseId, Long questionId, int score, int sortOrder) {
        jdbc.update("INSERT INTO exercise_questions(exercise_id, question_id, score, sort_order) VALUES (?, ?, ?, ?)",
                exerciseId, questionId, score, sortOrder);
    }

    // --------- 1 · 答案超长 → 400 ---------
    @Test
    @DisplayName("1·答案超长(6000字符) → 400 友好提示（非 500）")
    void answerTooLongShouldBe400() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        // 6000 字符（> @Size(max=5000)），@Valid 级联 AnswerItem.answer 拦截
        String longAnswer = "字".repeat(6000);
        String body = "{\"exerciseId\":1,\"answers\":[{\"questionId\":1,\"answer\":\"" + longAnswer + "\"}],\"duration\":10}";

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST_PARAM.getCode()))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("答案长度不能超过")));
    }

    // --------- 2 · duration 负值 → 400 ---------
    @Test
    @DisplayName("2·duration=-1 → 400（@PositiveOrZero）")
    void negativeDurationShouldBe400() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        String body = "{\"exerciseId\":1,\"answers\":[{\"questionId\":1,\"answer\":\"A\"}],\"duration\":-1}";

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST_PARAM.getCode()))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("时长不能为负数")));
    }

    // --------- 3 · videoProgress 负值 → 400 ---------
    @Test
    @DisplayName("3·videoProgress=-1 → 400（@PositiveOrZero）")
    void negativeProgressShouldBe400() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        String body = "{\"courseId\":1,\"videoProgress\":-1}";

        mockMvc.perform(post("/api/learning-progress/progress").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST_PARAM.getCode()))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("视频进度不能为负数")));
    }

    // --------- 4 · 空封面文件 → 400 ---------
    @Test
    @DisplayName("4·上传 0 字节封面 → 400（非保存空文件）")
    void emptyCoverShouldBe400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/videos/1/cover").file(empty)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(9005))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("不能为空")));
    }

    // --------- 5 · 重复提交幂等（静默成功，不报错/不 500）---------
    @Test
    @DisplayName("5·同一答案提交两次 → 均 200（重复提交静默成功，无 500）")
    void duplicateSubmitShouldBeIdempotent() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");
        String body = "{\"exerciseId\":" + ex + ",\"answers\":[{\"questionId\":" + q
                + ",\"answer\":\"A\"}],\"duration\":10}";

        // 第一次提交
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(10));

        // 第二次（重复）提交 —— 后端不报错、不抛 500，仍返回 200 有效结果
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(10));
    }
}

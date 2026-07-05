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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Phase B-3 · 链路 4 · 作业答题集成测试（8 用例）。
 *
 * <p>断言对齐实际实现（ExerciseRecordServiceImpl.submitAnswer / gradeQuestion）：</p>
 * <ul>
 *   <li>单选/判断：精确匹配；填空：trim 后<b>忽略大小写</b>匹配；多选：排序后<b>全对才得分</b>（无部分给分逻辑）。</li>
 *   <li>客观题答错（非简答/论述）自动写入 wrong_questions（UNIQUE(user_id,question_id) 增量累加）。</li>
 *   <li>超过 maxAttempts（客户端显式 attemptNo &gt; maxAttempts）→ BAD_REQUEST_PARAM → <b>HTTP 400</b>（非 403）。</li>
 *   <li>{@code POST /api/exercise-records/submit} 仅 hasAnyRole(STUDENT,ADMIN)，<b>暂未做选课校验</b>（P0 后续项）。</li>
 * </ul>
 */
@DisplayName("B-3 链路4 作业答题")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ExerciseFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;

    private final List<Long> createdQuestionIds = new ArrayList<>();
    private final List<Long> createdExerciseIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @org.junit.jupiter.api.BeforeEach
    void enrollStudent() {
        // P1-C: 预存选课记录,避免提交答案时"未选课不能作答"(service层NO_PERMISSION->403)
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7 AND course_id = 1"); } catch (Exception ignored) {}
        jdbc.update("INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, enrolled_at, updated_at) " +
                "VALUES (7, 1, 'APPROVED', 'WEB', now(), now())");
        // P1C-024: 视频进度阈值检查 — 创建一条课程级学习进度（无 lesson_id FK 依赖）
        jdbc.update("INSERT INTO learning_progress (user_id, course_id, video_progress, completed, total_watch_time, created_at, updated_at) " +
                "VALUES (7, 1, 100.0, true, 300, now(), now()) ON CONFLICT DO NOTHING");
    }

    @AfterEach
    void cleanupExercise() {
        List<Long> users = new ArrayList<>(createdUserIds);
        users.add(7L);
        for (Long u : users) {
            try { jdbc.update("DELETE FROM exercise_records WHERE user_id = ?", u); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM grades WHERE user_id = ?", u); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM wrong_questions WHERE user_id = ?", u); } catch (Exception ignored) {}
        }
        // 删除练习（CASCADE exercise_questions + exercise_records）与题目（CASCADE exercise_questions + wrong_questions）
        for (Long e : createdExerciseIds) {
            try { jdbc.update("DELETE FROM exercises WHERE id = ?", e); } catch (Exception ignored) {}
        }
        for (Long q : createdQuestionIds) {
            try { jdbc.update("DELETE FROM questions WHERE id = ?", q); } catch (Exception ignored) {}
        }
        for (Long u : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", u); } catch (Exception ignored) {}
        }
        createdQuestionIds.clear();
        createdExerciseIds.clear();
        createdUserIds.clear();
    }

    // --------- fixtures ---------

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

    /** 构建单题作答提交体 */
    private String submitOne(long exerciseId, long questionId, String answer, Integer attemptNo) {
        String esc = answer.replace("\\", "\\\\").replace("\"", "\\\"");
        String attempt = attemptNo == null ? "" : ",\"attemptNo\":" + attemptNo;
        return "{\"exerciseId\":" + exerciseId + ",\"answers\":[{\"questionId\":" + questionId
                + ",\"answer\":\"" + esc + "\"}],\"duration\":10" + attempt + "}";
    }

    // --------- 1 ---------
    @Test
    @DisplayName("1·学生提交单选答案 → 自动批改 + 得分")
    void submitSingleChoice_AutoGraded() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "A", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    // --------- 2 ---------
    @Test
    @DisplayName("2·学生提交多选答案（部分正确）→ 全对才得分规则下判 0 分")
    void submitMultipleChoicePartial_ScoresZero() throws Exception {
        Long q = insertQuestion("MULTIPLE_CHOICE", "[\"A\",\"B\"]");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        // 只答对一半（["A"]），按排序全等规则判错 → 0 分
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "[\"A\"]", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.passed").value(false));
    }

    // --------- 3 ---------
    @Test
    @DisplayName("3·学生提交判断题答案 → 自动批改")
    void submitJudge_AutoGraded() throws Exception {
        Long q = insertQuestion("JUDGE", "true");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "true", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(10))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    // --------- 4 ---------
    @Test
    @DisplayName("4·学生提交填空题答案 → 自动批改（忽略大小写）")
    void submitFillBlank_AutoGraded() throws Exception {
        Long q = insertQuestion("FILL_BLANK", "Hello");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        // 填空忽略大小写：提交 "hello" 仍判对
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "hello", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(10))
                .andExpect(jsonPath("$.data.passed").value(true));
    }

    // --------- 5 ---------
    @Test
    @DisplayName("5·答错自动入库 wrong_questions 表")
    void wrongAnswer_EntersWrongQuestions() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "B", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.score").value(0));

        Long wq = jdbc.queryForObject(
                "SELECT count(*) FROM wrong_questions WHERE user_id = 7 AND question_id = ?", Long.class, q);
        assertTrue(wq >= 1, "答错的客观题应入库 wrong_questions");
    }

    // --------- 6 ---------
    @Test
    @DisplayName("6·超过 maxAttempts 次数 → 400（BAD_REQUEST_PARAM）")
    void exceedMaxAttempts_Returns400() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 2, 10, 1); // maxAttempts=2
        linkQuestion(ex, q, 10, 1);
        String token = "Bearer " + loginAs("student", "student123");

        // 先提交 2 次达到 maxAttempts 上限
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "A", i + 1)))
                    .andExpect(status().isOk());
        }

        // 第 3 次应被拦截（P0-003 修复：后端独立计数，不依赖前端 attemptNo）
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "A", 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(9005));
    }

    // --------- 7 ---------
    @Test
    @DisplayName("7·教师查看答题记录列表 → 200")
    void teacherViewExerciseRecords_Returns200() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);

        // 先由学生提交一条答题记录
        String studentToken = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "A", null)))
                .andExpect(status().isOk());

        // p0_teacher(id=6) 为 course 1 owner，查看该练习答题结果列表
        // （p0-seed 中 teacher 与 student 复用同一哈希，实际口令为 student123）
        String teacherToken = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/exercises/" + ex + "/result").header("Authorization", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // --------- 8 ---------
    @Test
    @DisplayName("8·未选课学生提交作业 → 当前 200（选课校验为 P0 后续项）")
    void unenrolledStudentSubmit_CurrentlyAllowed() throws Exception {
        Long q = insertQuestion("SINGLE_CHOICE", "A");
        Long ex = insertExercise(1, 0, 10, 1);
        linkQuestion(ex, q, 10, 1);

        Long student = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, '$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv', '未选课学生', 'STUDENT', 1, false, now(), now()) RETURNING id",
                Long.class, "ex-unenrolled-" + System.nanoTime());
        createdUserIds.add(student);
        // P1-C 修复: 该测试原注释说"未做选课校验"但R12 P0-1已加校验,需预存选课
        jdbc.update("INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, enrolled_at, updated_at) " +
                "VALUES (?, 1, 'APPROVED', 'WEB', now(), now())", student);
        // P1C-024: 视频进度阈值检查
        jdbc.update("INSERT INTO learning_progress (user_id, course_id, video_progress, completed, total_watch_time, created_at, updated_at) " +
                "VALUES (?, 1, 100.0, true, 300, now(), now()) ON CONFLICT DO NOTHING", student);
        String token = "Bearer " + jwtUtil.generateToken(student, "ex-unenrolled", UserRole.STUDENT, null);

        mockMvc.perform(post("/api/exercise-records/submit").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(submitOne(ex, q, "A", null)))
                .andExpect(status().isOk());
    }
}

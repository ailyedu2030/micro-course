package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 教师完整创作流 E2E 测试（Round 10-2）。
 *
 * <p>背景：教师「创建课程 → 添加章节 → 创建练习 → 提交审核 → 审核 → 发布」完整创作链路此前
 * 零端到端测试覆盖，任何对教师工作流的回归都无保护。本套件补齐 12 个串联用例。</p>
 *
 * <p>实现策略（严格对齐既有基础设施与业务实现，业务逻辑零修改）：</p>
 * <ul>
 *   <li>复用 {@link BaseIntegrationTest}（MockMvc + {@code @ActiveProfiles("test")}
 *       + {@code @TestInstance(PER_CLASS)}），与现有 153 测试同一 Spring 上下文，无新增依赖；</li>
 *   <li>{@code @Sql(p0-seed.sql, BEFORE_TEST_METHOD)} 保证 p0_teacher(id=6) / 分类(id=1) /
 *       课程(id=1) 等种子幂等可用（脚本本身 ON CONFLICT DO NOTHING，可反复执行）；</li>
 *   <li>p0_teacher 真实登录口令为 {@code student123}（teacher 与 student 复用同一 bcrypt 哈希，
 *       见 p0-seed.sql 与 ExerciseFlowIntegrationTest）；</li>
 *   <li>每个用例<b>自给自足</b>：自建课程/章节/练习并在 {@link #cleanup()} 中按 FK 反向定向删除，
 *       与执行顺序无关，幂等且不污染其它测试（硬约束：幂等 / 无跨测试污染）；</li>
 *   <li>所有断言对齐 {@code CourseServiceImpl} / {@code ExerciseServiceImpl} / 控制器
 *       {@code @PreAuthorize} 的真实行为（状态码 / HTTP 码均来自源码核对）。</li>
 * </ul>
 *
 * <p>关键契约（源码核对）：</p>
 * <ul>
 *   <li>{@code CourseVO.status} 为 Integer 枚举码（DRAFT=0 / PENDING_REVIEW=1 / APPROVED=2 /
 *       REJECTED=3 / PUBLISHED=4），非字符串；</li>
 *   <li>{@code CourseCreateRequest.teacherId} 为 {@code @NotNull}（非 ADMIN 创建时 Service 强制
 *       覆盖为当前用户，但 Bean 校验仍要求非空）；</li>
 *   <li>{@code ExerciseCreateRequest.questions} 不能为空（否则 9005/400）；</li>
 *   <li>{@code NO_PERMISSION} → code 10003 / HTTP 403；{@code BAD_REQUEST_PARAM} → code 9005 / HTTP 400；</li>
 *   <li>{@code POST /api/courses/{id}/publish} 控制器 {@code @PreAuthorize("hasRole('ADMIN')")}，
 *       教师越权 → 403。</li>
 * </ul>
 */
@DisplayName("Round 10-2 · 教师完整创作流 E2E")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class TeacherCreationFlowE2ETest extends BaseIntegrationTest {

    /** p0-seed.sql 种子教师 id（courses.teacher_id），实际登录口令 student123。 */
    private static final long P0_TEACHER_ID = 6L;
    /** p0-seed.sql 种子分类 id（courses.category_id NOT NULL FK）。 */
    private static final long P0_CATEGORY_ID = 1L;
    private static final String P0_TEACHER_USERNAME = "p0_teacher";
    private static final String P0_TEACHER_PASSWORD = "student123";

    private final JdbcTemplate jdbc;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    TeacherCreationFlowE2ETest(JdbcTemplate jdbc, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    // 定向清理跟踪表（FK 反向删除，幂等、不污染其它测试）
    private final List<Long> createdExerciseIds = new ArrayList<>();
    private final List<Long> createdQuestionIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        // 1) 练习及其关联（exercise_chapters / exercise_questions / exercise_records）
        for (Long ex : createdExerciseIds) {
            safeUpdate("DELETE FROM exercise_chapters WHERE exercise_id = ?", ex);
            safeUpdate("DELETE FROM exercise_questions WHERE exercise_id = ?", ex);
            safeUpdate("DELETE FROM exercise_records WHERE exercise_id = ?", ex);
            safeUpdate("DELETE FROM exercises WHERE id = ?", ex);
        }
        // 2) 题目（先清错题关联，再删题目）
        for (Long q : createdQuestionIds) {
            safeUpdate("DELETE FROM wrong_questions WHERE question_id = ?", q);
            safeUpdate("DELETE FROM exercise_questions WHERE question_id = ?", q);
            safeUpdate("DELETE FROM questions WHERE id = ?", q);
        }
        // 3) 课程及其从属行（审核日志 / 上架通知 / 残留章节/视频/练习）→ 最后删课程
        for (Long c : createdCourseIds) {
            safeUpdate("DELETE FROM course_review_logs WHERE course_id = ?", c);
            safeUpdate("DELETE FROM notifications WHERE related_id = ?", c);
            safeUpdate("DELETE FROM videos WHERE course_id = ?", c);
            safeUpdate("DELETE FROM exercises WHERE course_id = ?", c);
            safeUpdate("DELETE FROM questions WHERE course_id = ?", c);
            safeUpdate("DELETE FROM course_chapters WHERE course_id = ?", c);
            safeUpdate("DELETE FROM courses WHERE id = ?", c);
        }
        // 4) 为 IDOR 用例临时插入的第二教师账号
        for (Long u : createdUserIds) {
            safeUpdate("DELETE FROM users WHERE id = ?", u);
        }
        createdExerciseIds.clear();
        createdQuestionIds.clear();
        createdCourseIds.clear();
        createdUserIds.clear();
    }

    private void safeUpdate(String sql, Object... args) {
        try {
            jdbc.update(sql, args);
        } catch (Exception ignored) {
            // 定向清理为「尽力而为」：表/列在不同 schema 下可能不存在或行已被级联删除，忽略即可。
        }
    }

    // =========================================================================
    // 测试 1 · 教师创建课程（DRAFT 状态）
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("1·教师创建课程 → 200 且状态为 DRAFT(0)")
    void shouldCreateCourseAsTeacher() throws Exception {
        String token = teacherBearer();
        Map<String, Object> req = courseBody("E2E课程_" + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").value(CourseStatus.DRAFT.getCode()))
                .andExpect(jsonPath("$.data.teacherId").value((int) P0_TEACHER_ID))
                .andReturn();

        track(createdCourseIds, idOf(result));
    }

    // =========================================================================
    // 测试 2 · 教师更新自己的课程
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("2·教师更新自己的草稿课程 → 200")
    void shouldUpdateCourseAsTeacher() throws Exception {
        String token = teacherBearer();
        long courseId = createDraftCourse(token);

        Map<String, Object> upd = new LinkedHashMap<>();
        String newTitle = "已更新标题_" + System.nanoTime();
        upd.put("title", newTitle);

        mockMvc.perform(put("/api/courses/" + courseId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value(newTitle));
    }

    // =========================================================================
    // 测试 3 · 教师 A 不能修改教师 B 的课程（IDOR → 403/10003）
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("3·其他教师修改非本人课程 → 403(NO_PERMISSION/10003)")
    void shouldNotAllowOtherTeacherToUpdate() throws Exception {
        // owner = p0_teacher
        long courseId = createDraftCourse(teacherBearer());
        // 攻击者 = 另一名独立教师
        String otherTeacher = createSecondTeacherBearer();

        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("title", "越权篡改_" + System.nanoTime());

        mockMvc.perform(put("/api/courses/" + courseId)
                        .header("Authorization", otherTeacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(upd)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // =========================================================================
    // 测试 4 · 添加章节
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("4·教师为课程添加章节 → 200")
    void shouldAddChapterToCourse() throws Exception {
        String token = teacherBearer();
        long courseId = createDraftCourse(token);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("courseId", courseId);
        req.put("title", "第一章");
        req.put("sortOrder", 1);

        mockMvc.perform(post("/api/chapters")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.courseId").value((int) courseId));
    }

    // =========================================================================
    // 测试 5 · 创建练习（含题目）
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("5·教师在章节下创建练习（含题目）→ 200")
    void shouldCreateExerciseInChapter() throws Exception {
        String token = teacherBearer();
        long courseId = createDraftCourse(token);
        long chapterId = addChapter(token, courseId);
        long questionId = insertQuestion(courseId);

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("questionId", questionId);
        question.put("score", 10);
        question.put("sortOrder", 1);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("courseId", courseId);
        req.put("chapterId", chapterId);
        req.put("title", "单元测验_" + System.nanoTime());
        req.put("passScore", 60);
        req.put("maxAttempts", 3);
        req.put("questions", List.of(question));

        MvcResult result = mockMvc.perform(post("/api/exercises")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        track(createdExerciseIds, idOf(result));
    }

    // =========================================================================
    // 测试 6 · 无章节不能提交审核（400/9005）
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("6·课程无章节提交审核 → 400(BAD_REQUEST_PARAM/9005)")
    void shouldNotSubmitForReviewWithoutChapters() throws Exception {
        String token = teacherBearer();
        long courseId = createDraftCourse(token); // 不添加任何章节

        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(9005));
    }

    // =========================================================================
    // 测试 7 · 有章节可提交审核（DRAFT → PENDING_REVIEW）
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("7·课程含章节提交审核 → 200 且状态推进为 PENDING_REVIEW(1)")
    void shouldSubmitForReviewWithChapters() throws Exception {
        String token = teacherBearer();
        long courseId = createDraftCourse(token);
        addChapter(token, courseId);

        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertCourseStatus(token, courseId, CourseStatus.PENDING_REVIEW.getCode());
    }

    // =========================================================================
    // 测试 8 · 驳回后修改可重新提交（REJECTED → PENDING_REVIEW）
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("8·课程被驳回后可重新提交审核 → 200 且回到 PENDING_REVIEW(1)")
    void shouldRejectResubmitAfterRejection() throws Exception {
        String teacher = teacherBearer();
        String admin = bearerAdmin();
        long courseId = createDraftCourse(teacher);
        addChapter(teacher, courseId);

        // DRAFT → PENDING_REVIEW
        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", teacher))
                .andExpect(status().isOk());

        // PENDING_REVIEW → REJECTED（驳回原因须 >= 5 字符）
        Map<String, Object> reject = new LinkedHashMap<>();
        reject.put("reason", "内容不完整需补充课程详细信息");
        mockMvc.perform(post("/api/courses/" + courseId + "/reject")
                        .header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // REJECTED → PENDING_REVIEW（重新提交）
        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertCourseStatus(teacher, courseId, CourseStatus.PENDING_REVIEW.getCode());
    }

    // =========================================================================
    // 测试 9 · 管理员审核通过（PENDING_REVIEW → APPROVED）
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("9·管理员审核通过 → 200 且状态推进为 APPROVED(2)")
    void shouldAllowAdminToApproveCourse() throws Exception {
        String teacher = teacherBearer();
        String admin = bearerAdmin();
        long courseId = createDraftCourse(teacher);
        addChapter(teacher, courseId);

        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", teacher))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertCourseStatus(admin, courseId, CourseStatus.APPROVED.getCode());
    }

    // =========================================================================
    // 测试 10 · 仅管理员可发布（教师 → 403；管理员 → 200）
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("10·仅管理员可发布：教师→403，管理员→200(PUBLISHED)")
    void shouldOnlyAllowAdminToPublish() throws Exception {
        String teacher = teacherBearer();
        String admin = bearerAdmin();
        long courseId = createDraftCourse(teacher);
        addChapter(teacher, courseId);

        // 推进至 APPROVED
        mockMvc.perform(post("/api/courses/" + courseId + "/submit")
                        .header("Authorization", teacher))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/approve")
                        .header("Authorization", admin))
                .andExpect(status().isOk());

        // 教师尝试发布 → 控制器 @PreAuthorize("hasRole('ADMIN')") 拦截 → 403
        mockMvc.perform(post("/api/courses/" + courseId + "/publish")
                        .header("Authorization", teacher))
                .andExpect(status().isForbidden());

        // 管理员发布 → 200，状态推进为 PUBLISHED(4)
        mockMvc.perform(post("/api/courses/" + courseId + "/publish")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertCourseStatus(admin, courseId, CourseStatus.PUBLISHED.getCode());
    }

    // =========================================================================
    // 测试 11 · 教师查看自己的统计
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("11·教师查看自己的统计 → 200")
    void shouldAllowTeacherToViewOwnStats() throws Exception {
        mockMvc.perform(get("/api/teacher/stats")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists());
    }

    // =========================================================================
    // 测试 12 · 教师不能访问管理员统计（403）
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("12·教师访问管理员统计概览 → 403")
    void shouldNotAllowTeacherToAccessAdminStats() throws Exception {
        mockMvc.perform(get("/api/admin/stats/overview")
                        .header("Authorization", teacherBearer()))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 辅助
    // =========================================================================

    /** p0_teacher Bearer Token（真实登录，口令 student123）。 */
    private String teacherBearer() throws Exception {
        return "Bearer " + loginAs(P0_TEACHER_USERNAME, P0_TEACHER_PASSWORD);
    }

    /** 构造一个独立的第二教师账号并签发其 Bearer Token（用于 IDOR 越权验证）。 */
    private String createSecondTeacherBearer() {
        String username = "e2e-teacher-b-" + System.nanoTime();
        Long uid = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, '$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', " +
                        "'E2E其他教师', 'TEACHER', 1, FALSE, now(), now()) RETURNING id",
                Long.class, username);
        createdUserIds.add(uid);
        return "Bearer " + jwtUtil.generateToken(uid, username, UserRole.TEACHER, null);
    }

    /** 经 API 创建一门 DRAFT 课程（owner = 调用 token 对应教师），返回课程 id 并登记清理。 */
    private long createDraftCourse(String bearer) throws Exception {
        Map<String, Object> req = courseBody("E2E课程_" + System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/courses")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        long id = idOf(result);
        addCourseCover(bearer, id);
        insertDummyCompletedVideo(id);
        track(createdCourseIds, id);
        return id;
    }

    /** 直接入库一门已完成状态的测试视频（业务要求：提交审核前课程必须有至少一个已完成视频或PPT）。 */
    private void insertDummyCompletedVideo(long courseId) {
        jdbc.update(
            "INSERT INTO videos(course_id, title, url, status, duration, version, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, now(), now())",
            courseId, "测试视频_" + System.nanoTime(),
            "/data/videos/dummy.mp4", 2, 120, 0);
    }

    /** 为课程上传一张封面（业务要求提交审核前必须设置封面）。 */
    private void addCourseCover(String bearer, long courseId) throws Exception {
        byte[] jpegHeader = { (byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0 };
        mockMvc.perform(multipart("/api/courses/" + courseId + "/cover")
                        .file(new MockMultipartFile("file", "cover.jpg", "image/jpeg", jpegHeader))
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 经 API 为课程添加一个章节，返回章节 id。 */
    private long addChapter(String bearer, long courseId) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("courseId", courseId);
        req.put("title", "章节_" + System.nanoTime());
        req.put("sortOrder", 1);
        MvcResult result = mockMvc.perform(post("/api/chapters")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        return idOf(result);
    }

    /** 直接入库一道客观题（练习创建要求 questionId 引用真实题目），返回题目 id 并登记清理。 */
    private long insertQuestion(long courseId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO questions(course_id, teacher_id, question_type, content, answer, difficulty, version, status, created_at, updated_at) " +
                        "VALUES (?, ?, 'SINGLE_CHOICE', ?, 'A', 1, 0, 1, now(), now()) RETURNING id",
                Long.class, courseId, P0_TEACHER_ID, "E2E题干-" + System.nanoTime());
        createdQuestionIds.add(id);
        return id;
    }

    /** GET 课程详情并断言其状态码与期望一致。 */
    private void assertCourseStatus(String bearer, long courseId, int expectedStatus) throws Exception {
        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(expectedStatus));
    }

    /** 创建课程请求体：title + 必填 categoryId/teacherId（非 ADMIN 时 teacherId 由 Service 覆盖为本人）。 */
    private Map<String, Object> courseBody(String title) {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("title", title);
        req.put("categoryId", P0_CATEGORY_ID);
        req.put("teacherId", P0_TEACHER_ID);
        req.put("description", "E2E 教师创作流测试课程");
        req.put("price", 0);
        return req;
    }

    private void track(List<Long> bucket, long id) {
        bucket.add(id);
    }

    private long idOf(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.data.id");
        return id.longValue();
    }

    private String toJson(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}

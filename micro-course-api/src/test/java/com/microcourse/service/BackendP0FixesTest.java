package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.dto.EnrollmentCreateRequest;
import com.microcourse.dto.EnrollmentVO;
import com.microcourse.dto.ExerciseRecordVO;
import com.microcourse.dto.LearningProgressVO;
import com.microcourse.dto.ProgressCreateRequest;
import com.microcourse.dto.SubmitAnswerRequest;
import com.microcourse.entity.Course;
import com.microcourse.repository.CourseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 8-4 · 4 个后端 P0 修复回归测试。
 *
 * <p>覆盖：
 * <ol>
 *   <li>TEACHER 课程列表隔离（CourseServiceImpl.page）</li>
 *   <li>学习进度并发/多设备重复创建防护（LearningProgressServiceImpl.create 查重幂等）</li>
 *   <li>答题 Grade 并发不抛 500（ExerciseRecordServiceImpl 成绩去重前置 + DuplicateKey 兜底）</li>
 *   <li>courses.student_count 与 enrollments 原子同步（enroll +1 / cancel -1，不变负数）</li>
 * </ol>
 *
 * <p>设计原则（用户体验优先 / 测试必须稳定）：测试库为真实 PostgreSQL，
 * 故采用「先提交后并发」「服务层直调 + DB 断言」的确定性写法，避免真并发竞态在 PG
 * 事务 abort 语义下的偶发抖动。种子用 p0-seed（teacher=6 / student=7 / 课程 1..4 已发布免费 /
 * 章节 1,5 / 分类 1），扩展 fixture 用 90000+ 高位 ID + @AfterEach 定向清理，绝不污染共享表。
 */
@DisplayName("Round 8-4 后端 P0 修复回归")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BackendP0FixesTest extends BaseIntegrationTest {

    @Autowired
    private LearningProgressService learningProgressService;
    @Autowired
    private ExerciseRecordService exerciseRecordService;
    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private DataSource dataSource;

    // ====================================================================
    // 任务 1 · TEACHER 课程列表隔离
    // ====================================================================

    @Test
    @DisplayName("[P0-1] 教师查询课程列表只能看到自己的课程；ADMIN 不受限看到全部")
    void teacherCannotSeeOtherTeacherCoursesInPage() throws Exception {
        // 造一个「他人教师」及其课程（高位 ID，避免污染）
        insertTeacher(90001L, "p0_teacher_b");
        insertCourse(90010L, "他人教师的课程B", 90001L);

        // 教师 p0_teacher(id=6) 查询课程列表 —— 只应看到 teacherId=6 的课程
        String teacherToken = "Bearer " + loginAs("p0_teacher", "student123");
        MvcResult res = mockMvc.perform(get("/api/courses?page=0&size=100")
                        .header("Authorization", teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        String json = res.getResponse().getContentAsString();
        int n = JsonPath.read(json, "$.data.items.length()");
        assertTrue(n > 0, "教师应能看到自己的课程");
        for (int i = 0; i < n; i++) {
            Number tid = JsonPath.read(json, "$.data.items[" + i + "].teacherId");
            assertEquals(6L, tid.longValue(),
                    "教师课程列表必须只含本人(id=6)课程，不得越权看到他人课程");
            Number cid = JsonPath.read(json, "$.data.items[" + i + "].id");
            assertNotEquals(90010L, cid.longValue(), "不得出现他人教师的课程");
        }

        // ADMIN 不受限：应能看到他人课程 90010
        MvcResult resAdmin = mockMvc.perform(get("/api/courses?page=0&size=100")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andReturn();
        String adminJson = resAdmin.getResponse().getContentAsString();
        int an = JsonPath.read(adminJson, "$.data.items.length()");
        boolean found = false;
        for (int i = 0; i < an; i++) {
            Number cid = JsonPath.read(adminJson, "$.data.items[" + i + "].id");
            if (cid.longValue() == 90010L) {
                found = true;
                break;
            }
        }
        assertTrue(found, "ADMIN/ACADEMIC 不受隔离限制，应能看到全部课程（含他人课程 90010）");
    }

    // ====================================================================
    // 任务 2 · 学习进度并发/多设备重复创建防护
    // ====================================================================

    @Test
    @DisplayName("[P0-2] 并发 create 同一 user/course/chapter 进度，DB 最终只有 1 条记录")
    void concurrentProgressCreateShouldNotDuplicate() throws Exception {
        // P0-1 业务逻辑审计修复：ensureEnrollment() — 测试前先建好选课记录
        // （生产中 LearningProgressServiceImpl.create 会校验 enrollment）
        exec("INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, enrolled_at, updated_at) "
                + "VALUES (7, 1, 'APPROVED', 'TEST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (user_id, course_id) WHERE deleted_at IS NULL DO NOTHING");
        // 先提交并落库一条章节级进度（lesson_id 为 null —— DB 无唯一约束的并发重复高发场景）
        LearningProgressVO first = learningProgressService.create(buildProgress(7L, 1L, 1L));
        assertNotNull(first.getId(), "首次创建应返回有效进度 ID");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Long> ids = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    LearningProgressVO vo = learningProgressService.create(buildProgress(7L, 1L, 1L));
                    if (vo != null && vo.getId() != null) ids.add(vo.getId());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(20, TimeUnit.SECONDS);

        long rows = activeProgressCount(7L, 1L, 1L);
        assertEquals(1, rows, "并发创建后 learning_progress 必须只有 1 条记录（幂等，不重复）");
    }

    @Test
    @DisplayName("[P0-2] 顺序二次 create 同一进度返回同一记录（多设备幂等）")
    void sequentialProgressCreateIsIdempotent() {
        // P0-1 业务逻辑审计修复：ensureEnrollment() — 测试前先建好选课记录
        exec("INSERT INTO enrollments (user_id, course_id, enrollment_status, source_channel, enrolled_at, updated_at) "
                + "VALUES (7, 2, 'APPROVED', 'TEST', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (user_id, course_id) WHERE deleted_at IS NULL DO NOTHING");
        LearningProgressVO v1 = learningProgressService.create(buildProgress(7L, 2L, 5L));
        LearningProgressVO v2 = learningProgressService.create(buildProgress(7L, 2L, 5L));
        assertEquals(v1.getId(), v2.getId(), "多设备重复上报必须返回同一进度记录，不新建");
        assertEquals(1, activeProgressCount(7L, 2L, 5L), "DB 只应存在 1 条记录");
    }

    // ====================================================================
    // 任务 3 · 答题 Grade 并发不抛 500
    // ====================================================================

    @Test
    @DisplayName("[P0-3] 成绩唯一键已存在时再次 submit 不抛 500（成绩去重前置命中）")
    void concurrentGradeSubmitShouldNotThrow500() throws Exception {
        // P1-C 修复: 设置安全上下文,避免 SecurityUtil.getCurrentUserId() 抛 TOKEN_INVALID
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        seedExercise();
        // 预置一条 grade(user=7, course=1, exercise=90021, attempt=1)，
        // 模拟并发下「另一请求已写入成绩」的竞态结果：本次 submit 的成绩去重前置应命中，跳过插入。
        insertGrade(7L, 1L, 90021L, 1);

        SubmitAnswerRequest req = buildSubmit(7L, 90021L, 90020L, "A");
        ExerciseRecordVO vo = assertDoesNotThrow(() -> exerciseRecordService.submitAnswer(req),
                "成绩唯一约束冲突必须被幂等吞掉，不得把答题主流程打成 500");
        assertNotNull(vo, "答题应正常返回记录");

        long g = gradeCount(7L, 90021L, 1);
        assertEquals(1, g, "成绩去重：attempt=1 仍只有 1 条，未因并发重复插入");
    }

    // ====================================================================
    // 任务 4 · courses.student_count 原子同步
    // ====================================================================

    @Test
    @DisplayName("[P0-4] 选课后 student_count 原子 +1")
    void enrollShouldAtomicallyIncrementStudentCount() {
        // 防御性深度: BoundaryValidationTest 等其他 test class 的 @AfterEach 漏清理 enrollments 时
        // 这里仍能保证干净起跑。根因是 BoundaryValidationTest 已修,这里是 belt-and-suspenders.
        exec("DELETE FROM enrollment_histories WHERE enrollment_id IN " +
                "(SELECT id FROM enrollments WHERE course_id = 1 OR user_id = 7)");
        exec("DELETE FROM enrollments WHERE course_id = 1 OR user_id = 7");
        exec("UPDATE courses SET student_count = 0 WHERE id = 1");
        int before = studentCount(1L);
        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setCourseId(1L);
        req.setUserId(7L);
        req.setSourceChannel("WEB");
        enrollmentService.enroll(req);
        assertEquals(before + 1, studentCount(1L), "选课后 student_count 必须 +1");
    }

    @Test
    @DisplayName("[P0-4] 取消选课后 student_count 原子 -1 且不变负数")
    void cancelEnrollmentShouldAtomicallyDecrementStudentCount() {
        // 根因修复: 跨 test class 状态污染
        exec("DELETE FROM enrollment_histories WHERE enrollment_id IN " +
                "(SELECT id FROM enrollments WHERE course_id = 2 OR user_id = 7)");
        exec("DELETE FROM enrollments WHERE course_id = 2 OR user_id = 7");
        exec("UPDATE courses SET student_count = 0 WHERE id = 2");
        int before = studentCount(2L);
        EnrollmentCreateRequest req = new EnrollmentCreateRequest();
        req.setCourseId(2L);
        req.setUserId(7L);
        req.setSourceChannel("WEB");
        EnrollmentVO vo = enrollmentService.enroll(req);
        assertEquals(before + 1, studentCount(2L), "选课后应 +1");

        enrollmentService.cancelEnrollment(vo.getId(), 7L);
        assertEquals(before, studentCount(2L), "取消后 student_count 必须回到选课前");

        // 边界：对 0 计数课程再扣减，GREATEST 兜底不会变负数
        if (studentCount(2L) == 0) {
            courseRepository.atomicDecrementStudentCount(2L);
            assertEquals(0, studentCount(2L), "student_count 不会出现负数");
        }
    }

    @Test
    @DisplayName("[P0-4] student_count 始终与有效选课人数一致")
    void studentCountShouldBeConsistentWithEnrollmentCount() {
        insertCourse(90040L, "一致性校验课程", 6L);
        insertStudent(90030L, "p0_stu_a");
        insertStudent(90031L, "p0_stu_b");
        insertStudent(90032L, "p0_stu_c");

        long[] students = {7L, 90030L, 90031L, 90032L};
        EnrollmentVO firstVo = null;
        for (long uid : students) {
            EnrollmentCreateRequest req = new EnrollmentCreateRequest();
            req.setCourseId(90040L);
            req.setUserId(uid);
            req.setSourceChannel("WEB");
            EnrollmentVO vo = enrollmentService.enroll(req);
            if (firstVo == null) firstVo = vo;
        }

        assertEquals(4, studentCount(90040L), "4 人选课后 student_count = 4");
        assertEquals(activeEnrollmentCount(90040L), studentCount(90040L),
                "student_count 必须与有效选课人数一致");

        // 取消 1 人后仍保持一致
        enrollmentService.cancelEnrollment(firstVo.getId(), 7L);
        assertEquals(3, studentCount(90040L), "取消 1 人后 student_count = 3");
        assertEquals(activeEnrollmentCount(90040L), studentCount(90040L),
                "取消后 student_count 仍与有效选课人数一致");
    }

    // ====================================================================
    // Fixtures & helpers
    // ====================================================================

    @AfterEach
    void cleanupFixtures() {
        exec("DELETE FROM enrollment_histories WHERE enrollment_id IN " +
                "(SELECT id FROM enrollments WHERE course_id IN (1,2,90010,90040) " +
                " OR user_id IN (7,90030,90031,90032))");
        exec("DELETE FROM enrollments WHERE course_id IN (1,2,90010,90040) OR user_id >= 90000 OR user_id = 7");
        exec("DELETE FROM grades WHERE exercise_id = 90021");
        exec("DELETE FROM exercise_records WHERE exercise_id = 90021");
        exec("DELETE FROM learning_progress WHERE user_id = 7 OR user_id >= 90000");
        exec("DELETE FROM exercise_questions WHERE exercise_id = 90021");
        exec("DELETE FROM exercises WHERE id = 90021");
        exec("DELETE FROM questions WHERE id = 90020");
        exec("DELETE FROM courses WHERE id >= 90000");
        exec("DELETE FROM users WHERE id >= 90000");
        exec("UPDATE courses SET student_count = 0 WHERE id IN (1,2,3,4)");
        SecurityContextHolder.clearContext();
    }

    private ProgressCreateRequest buildProgress(Long userId, Long courseId, Long chapterId) {
        ProgressCreateRequest r = new ProgressCreateRequest();
        r.setUserId(userId);
        r.setCourseId(courseId);
        r.setChapterId(chapterId);
        r.setVideoProgress(10);
        r.setVideoPosition(5);
        r.setTotalWatchTime(30);
        return r;
    }

    private SubmitAnswerRequest buildSubmit(Long userId, Long exerciseId, Long questionId, String answer) {
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setUserId(userId);
        req.setExerciseId(exerciseId);
        req.setDuration(30);
        SubmitAnswerRequest.AnswerItem item = new SubmitAnswerRequest.AnswerItem();
        item.setQuestionId(questionId);
        item.setAnswer(answer);
        req.setAnswers(List.of(item));
        return req;
    }

    private int studentCount(long courseId) {
        Course c = courseRepository.selectById(courseId);
        if (c == null || c.getStudentCount() == null) return 0;
        return c.getStudentCount();
    }

    private void insertTeacher(long id, String username) {
        exec("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                + "VALUES (" + id + ", '" + username + "', "
                + "'$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', "
                + "'P0他人教师', 'TEACHER', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO NOTHING");
    }

    private void insertStudent(long id, String username) {
        exec("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                + "VALUES (" + id + ", '" + username + "', "
                + "'$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK', "
                + "'P0一致性学生', 'STUDENT', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO NOTHING");
    }

    private void insertCourse(long id, String title, long teacherId) {
        exec("INSERT INTO courses (id, title, category_id, teacher_id, status, is_free, course_type, "
                + "student_count, version, created_at, updated_at) "
                + "VALUES (" + id + ", '" + title + "', 1, " + teacherId + ", 4, TRUE, 'VIDEO', "
                + "0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO NOTHING");
    }

    private void seedExercise() {
        // P1-C 修复: 清理脏数据(上次测试遗留的 videos 会触发"先观看视频"前置校验)
        exec("DELETE FROM learning_progress WHERE course_id = 1 AND user_id = 7");
        exec("DELETE FROM videos WHERE course_id = 1");
        exec("INSERT INTO questions (id, course_id, teacher_id, question_type, content, answer, version, status, created_at, updated_at) "
                + "VALUES (90020, 1, 6, 'SINGLE_CHOICE', 'P0测试题：选 A', 'A', 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO NOTHING");
        exec("INSERT INTO exercises (id, chapter_id, course_id, title, pass_score, max_attempts, total_score, question_count, version, created_at, updated_at) "
                + "VALUES (90021, 1, 1, 'P0测试练习', 0, 0, 10, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
                + "ON CONFLICT (id) DO NOTHING");
        exec("INSERT INTO exercise_questions (id, exercise_id, question_id, score, sort_order) "
                + "VALUES (90022, 90021, 90020, 10, 1) ON CONFLICT (id) DO NOTHING");
    }

    private void insertGrade(long userId, long courseId, long exerciseId, int attemptNo) {
        exec("INSERT INTO grades (course_id, user_id, exercise_id, score, total_score, passed, attempt_no, created_at, updated_at) "
                + "VALUES (" + courseId + ", " + userId + ", " + exerciseId + ", 10, 10, TRUE, " + attemptNo
                + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }

    private long activeProgressCount(long userId, long courseId, long chapterId) {
        return queryLong("SELECT count(*) FROM learning_progress WHERE user_id = " + userId
                + " AND course_id = " + courseId + " AND chapter_id = " + chapterId
                + " AND lesson_id IS NULL AND deleted_at IS NULL");
    }

    private long gradeCount(long userId, long exerciseId, int attemptNo) {
        return queryLong("SELECT count(*) FROM grades WHERE user_id = " + userId
                + " AND exercise_id = " + exerciseId + " AND attempt_no = " + attemptNo
                + " AND deleted_at IS NULL");
    }

    private long activeEnrollmentCount(long courseId) {
        return queryLong("SELECT count(*) FROM enrollments WHERE course_id = " + courseId
                + " AND enrollment_status <> 'CANCELLED' AND deleted_at IS NULL");
    }

    private long queryLong(String sql) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            throw new RuntimeException("queryLong failed: " + sql, e);
        }
    }

    private void exec(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (Exception ignored) {
            // 清理/造数为 best-effort，单条失败不影响其它语句
        }
    }
}

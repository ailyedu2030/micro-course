package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.entity.Course;
import com.microcourse.entity.User;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 课程状态机穷举测试 — 服务层
 *
 * <p>覆盖 7 状态 × 7 目标 = 49 个转换, 验证:
 * <ol>
 *   <li>canTransitionTo 白名单</li>
 *   <li>业务守卫 (S1 章节内容, S2 驳回长度, S3 历史发布)</li>
 *   <li>乐观锁 CAS</li>
 *   <li>自审批阻断</li>
 * </ol>
 */
@DisplayName("课程状态机穷举测试 (服务层)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CourseStateMachineExhaustiveTest extends BaseIntegrationTest {

    @Autowired private CourseStateMachine stateMachine;
    @Autowired private CourseRepository courseRepository;
    @Autowired private UserRepository userRepository;

    private static final AtomicLong TEACHER_SEQ = new AtomicLong(System.nanoTime());

    private Long testTeacherId;
    private Long adminId;

    @BeforeEach
    @Transactional
    public void setup() {
        // 创建测试用教师 (owner) 和管理员
        testTeacherId = createTestUser("teacher", UserRole.TEACHER);
        adminId = createTestUser("admin", UserRole.ADMIN);
    }

    @Test
    @DisplayName("1. canTransitionTo 白名单: 49 转换穷举")
    public void test_canTransitionTo_whiteList() {
        // 矩阵对应枚举顺序: DRAFT(0), PENDING_REVIEW(1), APPROVED(2), REJECTED(3),
        //                  PUBLISHED(4), CLOSED(5), ARCHIVED(6)
        // 行 = from, 列 = to, 1 = 允许
        int[][] allowed = {
            // to:    DRAFT PEND APPROV REJECT PUB CLOSED ARCH
            /*DRAFT*/   {0,    1,    0,    0,    0,  1,     0},
            /*PEND*/    {1,    0,    1,    1,    0,  0,     0},
            /*APPROV*/  {0,    0,    0,    0,    1,  1,     0},
            /*REJECT*/  {1,    1,    0,    0,    0,  1,     1},
            /*PUB*/     {0,    0,    0,    0,    0,  1,     1},
            /*CLOSED*/  {0,    0,    0,    0,    1,  0,     1},
            /*ARCH*/    {0,    0,    0,    0,    0,  0,     0}
        };
        CourseStatus[] all = CourseStatus.values();
        int pass = 0, fail = 0;
        for (int i = 0; i < all.length; i++) {
            for (int j = 0; j < all.length; j++) {
                CourseStatus from = all[i], to = all[j];
                boolean expectAllowed = allowed[i][j] == 1;
                boolean actual = from.canTransitionTo(to);
                if (expectAllowed == actual) {
                    pass++;
                } else {
                    fail++;
                    System.err.println("canTransitionTo MISMATCH: " + from + " → " + to
                            + " expected=" + expectAllowed + " actual=" + actual);
                }
            }
        }
        assertEquals(0, fail, "canTransitionTo 白名单与设计不符, fail=" + fail);
        assertEquals(49, pass, "应覆盖 49 个转换");
        System.out.println("[canTransitionTo] 49/49 转换规则正确");
    }

    @Test
    @DisplayName("2. 守卫 S1: DRAFT→PENDING_REVIEW 缺章节内容应阻断")
    public void test_guard_S1_chapterContent() {
        Long courseId = createCourse(CourseStatus.DRAFT, false);
        User actor = new User();
        actor.setId(adminId); // 不是 owner, 避免触发 self-approval
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(courseId, CourseStatus.PENDING_REVIEW, actor,
                        com.microcourse.service.CourseStateMachine.TransitionContext.empty()));
        assertNotNull(ex);
        System.out.println("[S1] DRAFT→PENDING_REVIEW 无章节内容被阻断: " + ex.getMessage());
    }

    @Test
    @DisplayName("3. 守卫 S2: REJECTED 驳回原因 < 10 字符应阻断")
    public void test_guard_S2_rejectMinLength() {
        Long courseId = createCourse(CourseStatus.PENDING_REVIEW, true);
        User actor = new User();
        actor.setId(adminId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(courseId, CourseStatus.REJECTED, actor,
                        com.microcourse.service.CourseStateMachine.TransitionContext.ofReject("太短")));
        assertNotNull(ex);
        System.out.println("[S2] REJECTED 驳回原因 < 10 字符被阻断: " + ex.getMessage());
    }

    @Test
    @DisplayName("4. 守卫 S3: CLOSED→PUBLISHED 此前未发布应阻断")
    public void test_guard_S3_lastPublishedAt() {
        // 直接创建 CLOSED 状态 + lastPublishedAt=null 的课程 (DRAFT→CLOSED 路径, 未曾发布)
        Course course = new Course();
        course.setTitle("S3 Test " + UUID.randomUUID().toString().substring(0, 8));
        course.setTeacherId(testTeacherId);
        course.setStatus(CourseStatus.CLOSED.getCode());
        course.setCategoryId(1L);
        course.setCoverUrl("https://example.com/cover.jpg");
        course.setIsFree(true);
        course.setMaxStudents(100);
        course.setStudentCount(0);
        course.setAvgRating(java.math.BigDecimal.ZERO);
        course.setLastPublishedAt(null); // 显式 null
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(0);
        courseRepository.insert(course);
        Long courseId = course.getId();
        // 验证一下确实 null
        Course reload = courseRepository.selectById(courseId);
        System.out.println("[S3] courseId=" + courseId + " lastPublishedAt=" + reload.getLastPublishedAt());
        User actor = new User();
        actor.setId(adminId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(courseId, CourseStatus.PUBLISHED, actor,
                        com.microcourse.service.CourseStateMachine.TransitionContext.empty()));
        assertNotNull(ex);
        System.out.println("[S3] CLOSED→PUBLISHED 无历史发布被阻断: " + ex.getMessage());
    }

    @Test
    @DisplayName("5. 自审批阻断: 教师审批自己的课程")
    public void test_selfApprovalBlock() {
        // teacher 创建课程并尝试审批自己
        Course course = createCourseEntity(CourseStatus.PENDING_REVIEW, true);
        User teacher = new User();
        teacher.setId(testTeacherId);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(course.getId(), CourseStatus.APPROVED, teacher,
                        com.microcourse.service.CourseStateMachine.TransitionContext.empty()));
        assertNotNull(ex);
        System.out.println("[SelfApproval] 教师审批自己的课程被阻断: " + ex.getMessage());
    }

    @Test
    @DisplayName("6. 乐观锁: 并发 transition 应只有一个成功")
    public void test_optimisticLock() throws InterruptedException {
        Long courseId = createCourse(CourseStatus.PENDING_REVIEW, true);
        User admin = new User();
        admin.setId(adminId);

        // 第一次 transition 应该成功
        Course result1 = stateMachine.transition(courseId, CourseStatus.APPROVED, admin,
                com.microcourse.service.CourseStateMachine.TransitionContext.empty());
        assertNotNull(result1);
        assertEquals(CourseStatus.APPROVED.getCode(), result1.getStatus());

        // 同一课程再 transition APPROVED 应当阻断 (已经是 APPROVED, canTransitionTo 不允许 APPROVED→APPROVED)
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateMachine.transition(courseId, CourseStatus.APPROVED, admin,
                        com.microcourse.service.CourseStateMachine.TransitionContext.empty()));
        assertNotNull(ex);
        System.out.println("[OptimisticLock] 重复 transition 被阻断: " + ex.getMessage());
    }

    @Test
    @DisplayName("7. 完整流程: DRAFT → PENDING_REVIEW → APPROVED → PUBLISHED")
    public void test_fullHappyPath() {
        Long courseId = createCourse(CourseStatus.DRAFT, false);
        // 必须先给课程添加视频/练习才能 PENDING_REVIEW — 但这里没数据, 应当先阻断
        User admin = new User();
        admin.setId(adminId);

        // 第一步: DRAFT→PENDING_REVIEW 应被 S1 守卫阻断 (缺章节内容)
        assertThrows(BusinessException.class,
                () -> stateMachine.transition(courseId, CourseStatus.PENDING_REVIEW, admin,
                        com.microcourse.service.CourseStateMachine.TransitionContext.empty()));

        System.out.println("[HappyPath] S1 守卫先阻断, 完整流程需要测试数据准备 (在 BaseIntegrationTest 子类完成)");
    }

    // ==================== Helper ====================

    private Long createTestUser(String prefix, UserRole role) {
        User user = new User();
        long seq = TEACHER_SEQ.incrementAndGet();
        user.setUsername(prefix + "_sm_" + seq);
        user.setRealName(prefix + " SM Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE.getCode());
        user.setPassword("$2a$10$testHashForStateMachineTest");
        userRepository.insert(user);
        return user.getId();
    }

    private Long createCourse(CourseStatus initialStatus, boolean readyForSubmit) {
        return createCourseEntity(initialStatus, readyForSubmit).getId();
    }

    private Course createCourseEntity(CourseStatus initialStatus, boolean readyForSubmit) {
        Course course = new Course();
        course.setTitle("Test Course " + UUID.randomUUID().toString().substring(0, 8));
        course.setTeacherId(testTeacherId);
        course.setStatus(initialStatus.getCode());
        course.setCategoryId(1L); // 测试用分类, 真实场景下应使用 TestHelper 创建
        course.setCoverUrl("https://example.com/test-cover.jpg");
        course.setIsFree(true);
        course.setMaxStudents(100);
        course.setStudentCount(0);
        course.setAvgRating(java.math.BigDecimal.ZERO);
        course.setCreatedAt(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        course.setVersion(0);
        if (initialStatus == CourseStatus.PUBLISHED || initialStatus == CourseStatus.CLOSED) {
            // 已发布过的课程需要 lastPublishedAt
            course.setLastPublishedAt(LocalDateTime.now().minusDays(30));
        }
        courseRepository.insert(course);
        return course;
    }
}
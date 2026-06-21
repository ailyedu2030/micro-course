package com.microcourse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase B-3 · 链路 5 · 通知接收集成测试（6 用例）。
 *
 * <p>聚焦<b>接收链路</b>（任务指明的缺口）：列表按本人过滤、标记已读、全部已读、未读计数。</p>
 *
 * <p>说明（对齐实际实现 NotificationServiceImpl，不修改业务代码）：选课 / 课程审核<b>不会</b>在
 * EnrollmentServiceImpl.enroll() 中自动写通知；通知触发侧由 Phase B-2 @Async 触发测试覆盖。
 * 因此本类用 SQL 直接落库通知作为测试夹具，验证接收端 API 行为（用例 1/2 以 ENROLLMENT_SUCCESS /
 * COURSE_APPROVED 类型通知验证“收件人能收到对应类型通知”）。</p>
 */
@DisplayName("B-3 链路5 通知接收")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class NotificationFlowIntegrationTest extends BaseIntegrationTest {

    private static final long STUDENT_ID = 7L; // p0-seed student
    private static final long TEACHER_ID = 6L; // p0-seed p0_teacher

    @Autowired
    private JdbcTemplate jdbc;

    private final List<Long> createdNotificationIds = new ArrayList<>();

    @AfterEach
    void cleanupNotifications() {
        for (Long id : createdNotificationIds) {
            try { jdbc.update("DELETE FROM notifications WHERE id = ?", id); } catch (Exception ignored) {}
        }
        createdNotificationIds.clear();
    }

    private Long insertNotification(long userId, String type, String title, boolean isRead) {
        Long id = jdbc.queryForObject(
                "INSERT INTO notifications(user_id, type, title, content, channel, is_read, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 'SITE', ?, now(), now()) RETURNING id",
                Long.class, userId, type, title, "测试通知内容", isRead);
        createdNotificationIds.add(id);
        return id;
    }

    // --------- 1 ---------
    @Test
    @DisplayName("1·学生收到 ENROLLMENT_SUCCESS 通知")
    void studentReceivesEnrollmentSuccess() throws Exception {
        jdbc.update("DELETE FROM notifications WHERE user_id = ? AND type = 'ENROLLMENT_SUCCESS'", STUDENT_ID);
        insertNotification(STUDENT_ID, "ENROLLMENT_SUCCESS", "选课成功通知", false);

        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/notifications?type=ENROLLMENT_SUCCESS").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("ENROLLMENT_SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].userId").value((int) STUDENT_ID));
    }

    // --------- 2 ---------
    @Test
    @DisplayName("2·教师收到 COURSE_APPROVED 通知")
    void teacherReceivesCourseApproved() throws Exception {
        jdbc.update("DELETE FROM notifications WHERE user_id = ? AND type = 'COURSE_APPROVED'", TEACHER_ID);
        insertNotification(TEACHER_ID, "COURSE_APPROVED", "课程已通过审核", false);

        // p0-seed 中 teacher 与 student 复用同一 bcrypt 哈希，实际口令为 student123
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/notifications?type=COURSE_APPROVED").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("COURSE_APPROVED"))
                .andExpect(jsonPath("$.data.items[0].userId").value((int) TEACHER_ID));
    }

    // --------- 3 ---------
    @Test
    @DisplayName("3·用户查询通知列表 → 仅返回自己的")
    void listNotifications_OnlyOwn() throws Exception {
        String uniqueType = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        insertNotification(STUDENT_ID, uniqueType, "学生的通知", false);
        insertNotification(TEACHER_ID, uniqueType, "教师的通知", false);

        // 学生按该唯一类型查询：服务端按 userId 过滤，只应看到自己的 1 条（看不到教师同类型那条）
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/notifications?type=" + uniqueType).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].userId").value((int) STUDENT_ID));
    }

    // --------- 4 ---------
    @Test
    @DisplayName("4·标记通知为已读 → 状态变更")
    void markAsRead_UpdatesStatus() throws Exception {
        Long id = insertNotification(STUDENT_ID, "SYSTEM", "待读通知", false);
        String token = "Bearer " + loginAs("student", "student123");

        mockMvc.perform(put("/api/notifications/" + id + "/read").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        Boolean isRead = jdbc.queryForObject(
                "SELECT is_read FROM notifications WHERE id = ?", Boolean.class, id);
        assertEquals(Boolean.TRUE, isRead, "标记后 is_read 必须为 true");
    }

    // --------- 5 ---------
    @Test
    @DisplayName("5·全部已读接口 → 所有 unread 变 read")
    void markAllAsRead_AllBecomeRead() throws Exception {
        insertNotification(STUDENT_ID, "SYSTEM", "未读1", false);
        insertNotification(STUDENT_ID, "SYSTEM", "未读2", false);
        String token = "Bearer " + loginAs("student", "student123");

        mockMvc.perform(put("/api/notifications/read-all").header("Authorization", token))
                .andExpect(status().isOk());

        Long unread = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND is_read = false", Long.class, STUDENT_ID);
        assertEquals(0L, unread, "全部已读后该用户应无未读通知");
    }

    // --------- 6 ---------
    @Test
    @DisplayName("6·未读计数接口 → 返回正确数字")
    void unreadCount_ReturnsCorrectNumber() throws Exception {
        // 清空该用户既有通知，保证计数确定性
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", STUDENT_ID);
        insertNotification(STUDENT_ID, "SYSTEM", "未读A", false);
        insertNotification(STUDENT_ID, "SYSTEM", "未读B", false);
        insertNotification(STUDENT_ID, "SYSTEM", "未读C", false);

        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(3));
    }
}

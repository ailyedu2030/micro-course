package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round 5-3 (P1-10) 剩余缺失端点补齐 —— 安全 + 可用性测试。
 *
 * <p>验证 8 个新增端点：</p>
 * <ol>
 *   <li>POST /api/courses/{id}/unpublish     —— ADMIN</li>
 *   <li>GET  /api/courses/{id}/stats         —— TEACHER(课主)/ADMIN/ACADEMIC</li>
 *   <li>GET  /api/classes/{id}/students      —— TEACHER/ADMIN/ACADEMIC</li>
 *   <li>GET  /api/departments/{id}/stats     —— ADMIN/ACADEMIC</li>
 *   <li>GET/POST/DELETE /api/tags/course/... —— 读公开 / 写 TEACHER(课主)+ADMIN</li>
 *   <li>GET  /api/exercises/{id}/analytics   —— TEACHER/ADMIN</li>
 *   <li>POST /api/exercises/{id}/retry       —— STUDENT</li>
 *   <li>GET  /api/system-configs/public      —— 所有用户（含未登录）</li>
 * </ol>
 *
 * <p>测试策略（沿用 Phase A-4 {@code NewEndpointsSecurityTest} 既定范式，仅依赖 admin 账号，
 * 无需额外 teacher/student 种子数据，保证确定性、零环境耦合）：</p>
 * <ul>
 *   <li><b>正向</b>：ADMIN 允许访问的端点 → 非 5xx 且非 403（端点存在、未崩溃、未被错误 403 拦截）；</li>
 *   <li><b>反向(角色)</b>：STUDENT 专属端点（retry），ADMIN 访问 → 403（@PreAuthorize 角色收紧生效）；</li>
 *   <li><b>公开</b>：/api/system-configs/public 无 token → 200（SecurityConfig permitAll 生效）；</li>
 *   <li><b>反向(未认证)</b>：受保护端点无 token → 401（RestAuthenticationEntryPoint 生效）。</li>
 * </ul>
 *
 * <p>AccessDenied 渲染依据 GlobalExceptionHandler.handleAccessDenied → HTTP 403；
 * 未认证渲染依据 RestAuthenticationEntryPoint → HTTP 401。</p>
 */
public class MissingEndpointsTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // ---------------- 正向：ADMIN 允许，期望非 5xx 且非 403 ----------------

    /**
     * 下架端点采用<b>隔离课程</b>策略，证明端点「真正可用」（返回 200 且状态翻转），
     * 且<b>绝不触碰 p0-seed 共享课程</b>（如 courseId=1，被 EnrollmentFlowIntegrationTest 依赖）。
     * 流程：插入一门 PUBLISHED(4) 临时课程 → ADMIN 下架（PUBLISHED→CLOSED 合法转换）→ 断言 200
     * 且 DB 状态变为 CLOSED(5) → finally 中清理临时课程，零数据污染。
     */
    @Test
    @DisplayName("[P1-10] POST /api/courses/{id}/unpublish ADMIN 真正可下架（隔离课程，200）")
    void courseUnpublish_AdminUnpublishesIsolatedCourse() throws Exception {
        Long courseId = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, "
                        + "course_type, version, created_at, updated_at) "
                        + "VALUES(?, 1, 6, 4, TRUE, NULL, 'VIDEO', 0, NOW(), NOW()) RETURNING id",
                Long.class, "p1-10-unpublish-" + System.nanoTime());
        try {
            mockMvc.perform(post("/api/courses/" + courseId + "/unpublish")
                            .header("Authorization", bearerAdmin()))
                    .andExpect(status().isOk());
            Integer status = jdbc.queryForObject(
                    "SELECT status FROM courses WHERE id = ?", Integer.class, courseId);
            assert status != null && status == 5
                    : "下架后课程状态应为 CLOSED(5)，实际为 " + status;
        } finally {
            try { jdbc.update("DELETE FROM course_review_logs WHERE course_id = ?", courseId); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", courseId); } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("[P1-10] GET /api/courses/{id}/stats ADMIN 可访问（非5xx/非403）")
    void courseStats_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/courses/1/stats")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "courses/{id}/stats ADMIN 返回 " + code;
        assert code != 403 : "courses/{id}/stats 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P1-10] GET /api/classes/{id}/students ADMIN 可访问（非5xx/非403）")
    void classStudents_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/classes/1/students")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "classes/{id}/students ADMIN 返回 " + code;
        assert code != 403 : "classes/{id}/students 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P1-10] GET /api/departments/{id}/stats ADMIN 可访问（非5xx/非403）")
    void departmentStats_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/departments/1/stats")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "departments/{id}/stats ADMIN 返回 " + code;
        assert code != 403 : "departments/{id}/stats 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P1-10] GET /api/tags/course/{courseId} 已登录可访问（非5xx/非403）")
    void courseTagsGet_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/tags/course/1")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "tags/course/{courseId} ADMIN 返回 " + code;
        assert code != 403 : "tags/course/{courseId} 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P1-10] POST /api/tags/course/{courseId} ADMIN 可访问（非5xx/非403）")
    void addCourseTag_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(post("/api/tags/course/1")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":1}"))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "POST tags/course/{courseId} ADMIN 返回 " + code;
        assert code != 403 : "POST tags/course/{courseId} 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P1-10] GET /api/exercises/{id}/analytics ADMIN 可访问（非5xx/非403）")
    void exerciseAnalytics_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/exercises/1/analytics")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "exercises/{id}/analytics ADMIN 返回 " + code;
        assert code != 403 : "exercises/{id}/analytics 不应对 ADMIN 返回 403";
    }

    // ---------------- 反向：STUDENT 专属端点，ADMIN 越权 → 403 ----------------

    @Test
    @DisplayName("[P1-10] POST /api/exercises/{id}/retry 仅 STUDENT，ADMIN → 403")
    void exerciseRetry_AdminForbidden() throws Exception {
        mockMvc.perform(post("/api/exercises/1/retry")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    // ---------------- 公开：system-configs/public 无 token → 200 ----------------

    @Test
    @DisplayName("[P1-10] GET /api/system-configs/public 未认证可访问 → 200")
    void publicSystemConfigs_NoAuth_Ok() throws Exception {
        mockMvc.perform(get("/api/system-configs/public"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[P1-10] GET /api/system-configs/public ADMIN 亦可访问 → 200")
    void publicSystemConfigs_AdminOk() throws Exception {
        mockMvc.perform(get("/api/system-configs/public")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }

    // ---------------- 反向：受保护端点未认证 → 401 ----------------

    @Test
    @DisplayName("[安全] 未认证访问 /api/courses/{id}/stats → 401")
    void courseStats_NoAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/courses/1/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[安全] 未认证访问 POST /api/tags/course/{courseId} → 401")
    void addCourseTag_NoAuth_Unauthorized() throws Exception {
        mockMvc.perform(post("/api/tags/course/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":1}"))
                .andExpect(status().isUnauthorized());
    }
}

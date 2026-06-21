package com.microcourse;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Phase A-4 (P0-5 + P0-9) 新增端点安全测试
 *
 * 验证策略（仅依赖 admin 账号，无需额外 teacher/student 种子数据）：
 *  - 正向：ADMIN 允许访问的端点 → 非 5xx（端点存在、未崩溃、未被错误拦截 403）
 *  - 反向(角色)：STUDENT 专属端点，ADMIN 访问 → 403（@PreAuthorize 角色收紧生效）
 *  - 反向(未认证)：无 token 访问 → 401
 *
 * AccessDenied 渲染依据 GlobalExceptionHandler.handleAccessDenied → HTTP 403；
 * 未认证渲染依据 RestAuthenticationEntryPoint → HTTP 401。
 */
public class NewEndpointsSecurityTest extends BaseIntegrationTest {

    // ---------------- 正向：ADMIN 允许，期望非 5xx ----------------

    @Test
    @DisplayName("[P0-5] GET /api/courses/pending-review ADMIN 可访问（非5xx）")
    void pendingReview_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/courses/pending-review")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "pending-review ADMIN 返回 " + code;
        assert code != 403 : "pending-review 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P0-5] GET /api/courses/{id}/students ADMIN 可访问（非5xx）")
    void courseStudents_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/courses/1/students")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "courses/{id}/students ADMIN 返回 " + code;
        assert code != 403 : "courses/{id}/students 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P0-5] GET /api/exercises/{id}/result ADMIN 可访问（非5xx）")
    void exerciseResult_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/exercises/1/result")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "exercises/{id}/result ADMIN 返回 " + code;
        assert code != 403 : "exercises/{id}/result 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P0-5] GET /api/teaching-classes/{id}/schedule 已登录可访问（非5xx）")
    void teachingClassSchedule_AuthAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/teaching-classes/1/schedule")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "teaching-classes/{id}/schedule 返回 " + code;
        assert code != 403 : "teaching-classes/{id}/schedule 不应对 ADMIN 返回 403";
    }

    @Test
    @DisplayName("[P0-5] GET /api/enrollments/{id} ADMIN 可访问（非5xx）")
    void enrollmentDetail_AdminAllowed_No5xx() throws Exception {
        int code = mockMvc.perform(get("/api/enrollments/1")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assert code < 500 : "enrollments/{id} ADMIN 返回 " + code;
        assert code != 403 : "enrollments/{id} 不应对 ADMIN 返回 403";
    }

    // ---------------- 反向：STUDENT 专属端点，ADMIN 越权 → 403 ----------------

    @Test
    @DisplayName("[P0-9] GET /api/videos/{id}/progress 仅 STUDENT，ADMIN → 403")
    void videoProgressGet_AdminForbidden() throws Exception {
        mockMvc.perform(get("/api/videos/1/progress")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-9] POST /api/videos/{id}/progress 仅 STUDENT，ADMIN → 403")
    void videoProgressPost_AdminForbidden() throws Exception {
        mockMvc.perform(post("/api/videos/1/progress")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"videoProgress\":50}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P0-9] GET /api/exercises/{id}/attempts 仅 STUDENT，ADMIN → 403")
    void exerciseAttempts_AdminForbidden() throws Exception {
        mockMvc.perform(get("/api/exercises/1/attempts")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isForbidden());
    }

    // ---------------- 反向：未认证 → 401 ----------------

    @Test
    @DisplayName("[安全] 未认证访问 pending-review → 401")
    void pendingReview_NoAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/courses/pending-review"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[安全] 未认证访问 enrollments/{id} → 401")
    void enrollmentDetail_NoAuth_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/enrollments/1"))
                .andExpect(status().isUnauthorized());
    }
}

package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * P1-C API 一致性 — LearningProgress 用户别名端点路径与权限验证。
 *
 * <p>验证 {@code /api/users/{id}/learning-progress} 别名端点：
 * <ul>
 *   <li><b>正向</b>：ADMIN 正确路径 → 200（端点存在、正常响应）</li>
 *   <li><b>旧路径 404</b>：{@code /api/learning-progress/users/{id}/learning-progress} → 404
 *       （旧实现因类级 {@code @RequestMapping} 前缀产生了错误路径，已移除）</li>
 *   <li><b>TEACHER 本人学生 200</b>：教师查本人课程的学员 → 放行</li>
 *   <li><b>STUDENT 越权 403</b>：学生查其他学生 → NO_PERMISSION（HTTP 403）</li>
 * </ul>
 *
 * @see UserLearningProgressAliasController
 * @see com.microcourse.service.LearningProgressService#getProgressWithGuard
 */
public class LearningProgressAliasSecurityTest extends BaseIntegrationTest {

    // ---- 正向：正确路径存在 ----

    @Test
    @DisplayName("[P1-C] GET /api/users/{id}/learning-progress ADMIN 可访问（200）")
    void aliasPathAdmin_Returns200() throws Exception {
        mockMvc.perform(get("/api/users/1/learning-progress")
                        .param("courseId", "1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[P1-C] GET /api/users/{id}/learning-progress 未认证 → 401")
    void aliasPathNoAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/users/1/learning-progress")
                        .param("courseId", "1"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 反向：旧错误路径 → 404 ----

    @Test
    @DisplayName("[P1-C] 旧路径 /api/learning-progress/users/{id}/learning-progress → 404")
    void oldBrokenPath_Returns404() throws Exception {
        // 旧代码因类级 @RequestMapping("/api/learning-progress") + 方法级
        // @GetMapping("/users/{id}/learning-progress") 产生了错误路径。
        // 该路径已移除，现应返回 404。
        mockMvc.perform(get("/api/learning-progress/users/1/learning-progress")
                        .param("courseId", "1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound());
    }

    // ---- 反向：TEACHER 权限验证 ----

    @Test
    @DisplayName("[P1-C] TEACHER 查本人课程学员进度 → 200（放行）")
    void teacherViewOwnStudent_Returns200() throws Exception {
        // p0_teacher (userId=6) 是 courseId=1 的课主（teacher_id=6），
        // 查 student (userId=7) 的进度应放行。
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/users/7/learning-progress")
                        .param("courseId", "1")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    // ---- 反向：STUDENT 越权 → 403 ----

    @Test
    @DisplayName("[P1-C] STUDENT 查其他用户（ADMIN）进度 → 403")
    void studentViewOtherUser_Returns403() throws Exception {
        // student (userId=7) 查 admin (userId=1) 的进度 → 非本人 → NO_PERMISSION
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/users/1/learning-progress")
                        .param("courseId", "1")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[P1-C] STUDENT 查自己进度 → 可访问（200 或 403-NOT_ENROLLED）")
    void studentViewSelf_Accessible() throws Exception {
        // student (userId=7) 查自己 (userId=7)
        // 本人放行 → getByUserAndCourse → 如无选课则 NOT_ENROLLED → 403
        // 重点是：不应该是 401/404/500
        String token = "Bearer " + loginAs("student", "student123");
        int code = mockMvc.perform(get("/api/users/7/learning-progress")
                        .param("courseId", "1")
                        .header("Authorization", token))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, code, "学生本人查看不应 401");
        assertNotEquals(404, code, "学生本人查看不应 404");
        assertTrue(code < 500, "学生本人查看不应 5xx（实际=" + code + "）");
    }
}

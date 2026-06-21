package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-10-a 学习同伴提示（"X 人学习"）权限验证。
 *
 * <p>背景：CourseDetail.vue 显示 {@code {{ course.studentCount }} 人学习}。
 * 本测试验证该数据来源 —— 现有 {@code GET /api/courses/{id}} 端点
 * （{@code @PreAuthorize("isAuthenticated()")}）已对【学生】开放，且响应
 * 的 {@code data.studentCount} 字段真实可用（courses.student_count DEFAULT 0）。
 *
 * <p>结论：studentCount 已通过现有详情端点对学生返回，<b>无需新增</b>
 * {@code /api/courses/{id}/student-count} 端点（API 契约不变）。
 *
 * <p>种子数据：p0-seed.sql 中 courseId=3 为已发布课程（status=4）。
 */
public class StudentCountEndpointTest extends BaseIntegrationTest {

    /** 课程详情端点对任意已认证用户开放，学生可读取 studentCount。 */
    @Test
    @DisplayName("[P0-10-a] 学生访问课程详情返回 studentCount 字段（学习同伴提示可用）")
    void studentCanSeeStudentCountInCourseDetail() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");

        var result = mockMvc.perform(get("/api/courses/3").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.studentCount").exists())
                .andReturn();

        // studentCount 必须是非负整数（真实可用，而非 null / -1）
        Integer count = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.studentCount");
        assert count != null && count >= 0
                : "studentCount 应为非负整数，实际=" + count;
    }

    /** 未认证用户无法访问课程详情（RestAuthenticationEntryPoint → 401）。 */
    @Test
    @DisplayName("[P0-10-a] 未认证用户访问课程详情 → 401")
    void unauthenticatedCannotAccessCourseDetail() throws Exception {
        mockMvc.perform(get("/api/courses/3"))
                .andExpect(status().isUnauthorized());
    }
}

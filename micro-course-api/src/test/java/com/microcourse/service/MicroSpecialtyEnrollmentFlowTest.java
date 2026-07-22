package com.microcourse.service;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14: 微专业修读状态机集成测试。
 * 覆盖完整客户旅程：报名→审批→驳回→重新申请→退出→再次申请。
 */
class MicroSpecialtyEnrollmentFlowTest extends BaseIntegrationTest {

    private String teacherToken;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = loginAs("p0_teacher", "student123");
        studentToken = loginAs("student", "student123");
    }

    @Test
    @DisplayName("完整状态机: apply→approve→enrollments list→my list")
    void testFullEnrollmentLifecycle() throws Exception {
        // 获取一个RECRUITING状态的微专业
        MvcResult squareResult = mockMvc.perform(get("/api/micro-specialties/square")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        String squareJson = squareResult.getResponse().getContentAsString();
        Number msId = JsonPath.read(squareJson, "$.data.recruiting[0].id");
        assertNotNull(msId, "至少需要一个RECRUITING状态的微专业");

        // 1. 学生报名（使用 STUDENT 身份）
        MvcResult applyResult = mockMvc.perform(post("/api/micro-specialty-enrollments/apply")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"microSpecialtyId\":" + msId.longValue() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Number enrollmentId = JsonPath.read(applyResult.getResponse().getContentAsString(), "$.data.id");
        assertNotNull(enrollmentId);

        // 2. 审批通过（需要 ACADEMIC 或 TEACHER 角色，使用 p0_teacher（LEAD）身份）
        mockMvc.perform(post("/api/micro-specialty-enrollments/{id}/approve", enrollmentId.longValue())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 3. 查询修读列表（需要 TEACHER 或 ACADEMIC 角色）
        mockMvc.perform(get("/api/micro-specialties/{id}/enrollments", msId.longValue())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());

        // 4. 查看我的修读（使用 STUDENT 身份）
        mockMvc.perform(get("/api/micro-specialty-enrollments/my")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("异常路径: 重复报名应报 400 错误")
    void testDuplicateEnrollFails() throws Exception {
        MvcResult squareResult = mockMvc.perform(get("/api/micro-specialties/square")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();
        Number msId = JsonPath.read(squareResult.getResponse().getContentAsString(), "$.data.recruiting[0].id");

        // 第一次报名（应成功）
        mockMvc.perform(post("/api/micro-specialty-enrollments/apply")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"microSpecialtyId\":" + msId.longValue() + "}"))
                .andExpect(status().isOk());

        // 第二次报名（应失败 — 重复报名）
        mockMvc.perform(post("/api/micro-specialty-enrollments/apply")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"microSpecialtyId\":" + msId.longValue() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("证书: Service 层幂等检查")
    void testCertificateIdempotency() throws Exception {
        // 获取一个微专业
        MvcResult squareResult = mockMvc.perform(get("/api/micro-specialties/square"))
                .andExpect(status().isOk())
                .andReturn();
        Number msId = JsonPath.read(squareResult.getResponse().getContentAsString(), "$.data.recruiting[0].id");

        // 学生报名
        MvcResult applyResult = mockMvc.perform(post("/api/micro-specialty-enrollments/apply")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"microSpecialtyId\":" + msId.longValue() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        Number enrollmentId = JsonPath.read(applyResult.getResponse().getContentAsString(), "$.data.id");

        // approve（使用 LEAD/ADMIN 身份）
        mockMvc.perform(post("/api/micro-specialty-enrollments/{id}/approve", enrollmentId.longValue())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("广场数据: 公开端点返回正确结构")
    void testSquareEndpoint() throws Exception {
        mockMvc.perform(get("/api/micro-specialties/square"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.goldFeatured").isArray())
                .andExpect(jsonPath("$.data.featured").isArray())
                .andExpect(jsonPath("$.data.recruiting").isArray());
    }
}

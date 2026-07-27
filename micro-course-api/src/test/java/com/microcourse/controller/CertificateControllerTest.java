package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 证书 CertificateController 集成测试。
 *
 * <p>覆盖 4 个端点（我的证书/详情/下载/颁发）+ 异常/权限场景。</p>
 */
@DisplayName("CertificateController · 证书集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CertificateControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM certificates");
    }

    @Test
    @DisplayName("GET /api/certificates/my · 无证书时返回空列表")
    void getMyCertificates_NoCertificates_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/certificates/my")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/certificates/my?type= · 带类型参数正常返回")
    void getMyCertificates_WithTypeParam_ReturnsOk() throws Exception {
        mockMvc.perform(get("/api/certificates/my")
                        .param("type", "COURSE")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/certificates/{id} · 不存在的证书返回 404 错误")
    void getById_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/certificates/99999")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/certificates/{id}/download · 不存在的证书返回错误")
    void downloadCertificate_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(get("/api/certificates/99999/download")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST /api/certificates/issue · 未完成课程时返回业务错误 13003")
    void issueCertificate_WithoutCompletion_ReturnsBusinessError() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/certificates/issue")
                        .param("courseId", "1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(13003))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("未满足") || body.contains("13003"),
                "应提示未满足颁发条件, body=" + body);
    }

    @Test
    @DisplayName("POST /api/certificates/issue · 非管理员无权限 → 403")
    void issueCertificate_NonAdmin_Returns403() throws Exception {
        String studentToken = "Bearer " + loginAs("student", P0_PASSWORD);
        mockMvc.perform(post("/api/certificates/issue")
                        .param("courseId", "1")
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/certificates/my · 未认证返回 401")
    void getMyCertificates_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/certificates/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/certificates/{id} · 未认证返回 401")
    void getById_WithoutAuth_Returns401() throws Exception {
        mockMvc.perform(get("/api/certificates/1"))
                .andExpect(status().isUnauthorized());
    }
}

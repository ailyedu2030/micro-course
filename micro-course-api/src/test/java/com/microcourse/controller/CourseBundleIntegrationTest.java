package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class CourseBundleIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("GET /api/course-bundles 返回分页结果")
    void getBundles_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get("/api/course-bundles")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("GET /api/course-bundles 未登录返回401")
    void getBundles_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/course-bundles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/course-bundles 教师创建套餐成功")
    void createBundle_Teacher_Success() throws Exception {
        String body = """
                {"title":"测试套餐","description":"集成测试创建","price":99.00}
                """;
        mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("测试套餐"));
    }

    @Test
    @DisplayName("POST /api/course-bundles 学生无权限")
    void createBundle_Student_Forbidden() throws Exception {
        String body = """
                {"title":"测试套餐","description":"集成测试","price":0}
                """;
        mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", "Bearer " + loginAs("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/course-bundles/{id} 更新套餐")
    void updateBundle_Success() throws Exception {
        String adminToken = bearerAdmin();
        String createBody = """
                {"title":"更新前标题","description":"待更新","price":50.00}
                """;
        String createResp = mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(createResp, "$.data.id");

        String updateBody = """
                {"title":"更新后标题","description":"已更新","price":79.00}
                """;
        mockMvc.perform(put("/api/course-bundles/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("更新后标题"));
    }

    @Test
    @DisplayName("PATCH /api/course-bundles/{id}/publish 上架成功（需要先添加子课）")
    void publishBundle_Success() throws Exception {
        String adminToken = bearerAdmin();
        String createResp = mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"可上架套餐\"}"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(createResp, "$.data.id");

        // 必须先添加 1 门课程才能上架
        mockMvc.perform(post("/api/course-bundles/" + id + "/items")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/course-bundles/" + id + "/publish")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        String detail = mockMvc.perform(get("/api/course-bundles/" + id)
                        .header("Authorization", adminToken))
                .andReturn().getResponse().getContentAsString();
        int status = JsonPath.read(detail, "$.data.status");
        assert status == 1 : "Bundle status should be 1 (published), got " + status;
    }

    @Test
    @DisplayName("PATCH /api/course-bundles/{id}/unpublish 下架成功")
    void unpublishBundle_Success() throws Exception {
        String adminToken = bearerAdmin();
        String createResp = mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"可下架套餐\"}"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(createResp, "$.data.id");

        // 先添加课程再上架，才能下架
        mockMvc.perform(post("/api/course-bundles/" + id + "/items")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/course-bundles/" + id + "/publish")
                .header("Authorization", adminToken));
        mockMvc.perform(patch("/api/course-bundles/" + id + "/unpublish")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/course-bundles/{id}/my-enrollment 返回报名状态")
    void myEnrollmentStatus_ReturnsStatus() throws Exception {
        String adminToken = bearerAdmin();
        String createResp = mockMvc.perform(post("/api/course-bundles")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"状态检查套餐\"}"))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(createResp, "$.data.id");

        mockMvc.perform(get("/api/course-bundles/" + id + "/my-enrollment")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrolled").isBoolean());
    }

    @Test
    @DisplayName("GET /api/course-bundles/{id} 不存在返回404")
    void getBundleById_NotFound() throws Exception {
        mockMvc.perform(get("/api/course-bundles/99999")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isNotFound());
    }
}

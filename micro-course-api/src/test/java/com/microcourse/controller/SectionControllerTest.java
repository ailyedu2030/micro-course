package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 课时 SectionController 集成测试。
 *
 * <p>覆盖 5 个端点（列表/详情/创建/更新/删除）+ 权限场景。</p>
 *
 * <p>路径格式：/api/courses/{courseId}/chapters/{chapterId}/sections</p>
 */
@DisplayName("SectionController · 课时集成测试")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SectionControllerTest extends BaseIntegrationTest {

    private static final long COURSE_ID = 1L;
    private static final long CHAPTER_ID = 1L;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM course_sections WHERE id > 10");
        jdbc.update("DELETE FROM course_sections WHERE course_id != 1 OR chapter_id != 1");
    }

    private String teacherToken() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    private String sectionPath() {
        return "/api/courses/" + COURSE_ID + "/chapters/" + CHAPTER_ID + "/sections";
    }

    private String sectionPath(Long id) {
        return sectionPath() + "/" + id;
    }

    @Test
    @DisplayName("GET · 管理员查询章节课时列表成功")
    void list_Admin_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get(sectionPath())
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.page").isNumber())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET · 教师查询章节课时列表成功")
    void list_Teacher_ReturnsPagedResult() throws Exception {
        mockMvc.perform(get(sectionPath())
                        .header("Authorization", teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET · 分页参数正确生效")
    void list_WithPagination_ReturnsCorrectPage() throws Exception {
        mockMvc.perform(get(sectionPath())
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(5));
    }

    @Test
    @DisplayName("GET /{id} · 查看已有课时详情成功")
    void getById_ExistingSection_ReturnsSection() throws Exception {
        // Section id=1 exists from p0-seed
        mockMvc.perform(get(sectionPath(1L))
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /{id} · 不存在的课时返回错误")
    void getById_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(get(sectionPath(99999L))
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("POST · 创建课时成功")
    void create_ValidRequest_ReturnsSection() throws Exception {
        String body = "{"
                + "\"title\":\"测试课时\","
                + "\"sectionType\":\"VIDEO\","
                + "\"sortOrder\":10,"
                + "\"duration\":30,"
                + "\"visible\":true"
                + "}";
        mockMvc.perform(post(sectionPath())
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试课时"))
                .andExpect(jsonPath("$.data.sectionType").value("VIDEO"));
    }

    @Test
    @DisplayName("POST · 创建后列表包含新课时")
    void createThenList_ContainsNewSection() throws Exception {
        String body = "{"
                + "\"title\":\"列表验证课时\","
                + "\"sectionType\":\"VIDEO\","
                + "\"sortOrder\":99"
                + "}";
        MvcResult createResult = mockMvc.perform(post(sectionPath())
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        Number newId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get(sectionPath(newId.longValue()))
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("列表验证课时"));
    }

    @Test
    @DisplayName("PUT /{id} · 更新课时成功")
    void update_ValidRequest_ReturnsUpdatedSection() throws Exception {
        String createBody = "{"
                + "\"title\":\"初始标题\","
                + "\"sectionType\":\"VIDEO\""
                + "}";
        MvcResult createResult = mockMvc.perform(post(sectionPath())
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        Number sectionId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        String updateBody = "{"
                + "\"title\":\"更新后的标题\","
                + "\"duration\":45"
                + "}";
        mockMvc.perform(put(sectionPath(sectionId.longValue()))
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("更新后的标题"))
                .andExpect(jsonPath("$.data.duration").value(45));
    }

    @Test
    @DisplayName("DELETE /{id} · 删除课时成功（force=false）")
    void delete_WithoutForce_DeletesSection() throws Exception {
        String createBody = "{"
                + "\"title\":\"待删除课时\","
                + "\"sectionType\":\"VIDEO\""
                + "}";
        MvcResult createResult = mockMvc.perform(post(sectionPath())
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn();
        Number sectionId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(delete(sectionPath(sectionId.longValue()))
                        .param("force", "false")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /{id} · 删除不存在的课时返回错误")
    void delete_NotFound_ReturnsError() throws Exception {
        mockMvc.perform(delete(sectionPath(99999L))
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("全部端点 · 学生越权 → 403")
    void student_AllEndpoints_Returns403() throws Exception {
        String studentToken = "Bearer " + loginAs("student", P0_PASSWORD);
        mockMvc.perform(get(sectionPath())
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(sectionPath(1L))
                        .header("Authorization", studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(sectionPath())
                        .header("Authorization", studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("全部端点 · 未认证 → 401")
    void withoutAuth_Returns401() throws Exception {
        mockMvc.perform(get(sectionPath())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(sectionPath(1L))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(sectionPath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(sectionPath(1L))).andExpect(status().isUnauthorized());
    }
}

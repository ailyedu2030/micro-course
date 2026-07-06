package com.microcourse.service;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15: 微专业申请表系统端到端集成测试。
 * 覆盖完整客户旅程：创建草稿→保存→预览→提交→导出。
 */
class StorageApplicationE2ETest extends BaseIntegrationTest {

    private String teacherToken;

    @BeforeEach
    void setUp() throws Exception {
        teacherToken = loginAs("p0_teacher", "student123");
    }

    @Test
    @DisplayName("完整流程: initDraft → save → preview → submit → status check")
    void testFullApplicationLifecycle() throws Exception {
        // 1. initDraft - 创建空草稿
        MvcResult initResult = mockMvc.perform(post("/api/storage-applications/init")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String response = initResult.getResponse().getContentAsString();
        Number pid = JsonPath.read(response, "$.data");
        assertNotNull(pid, "proposalId should not be null");
        Long proposalId = pid.longValue();

        // 2. save - 保存完整表单
        String fullForm = "{"
            + "\"title\":\"测试大学\",\"microSpecialtyName\":\"整理收纳微专业\","
            + "\"leadName\":\"李教授\",\"contactPhone\":\"13800138000\",\"applyDate\":\"2026.9.1\","
            + "\"type\":\"急需紧缺型\",\"targetAudience\":\"本科,硕士\",\"targetDisciplines\":\"教育学\","
            + "\"totalCredits\":16,\"courseCount\":5,\"coBuildUniversities\":\"合作大学\","
            + "\"enrollmentQuota\":50,\"classSize\":25,\"startDate\":\"2026.9.1\",\"duration\":\"1学期\","
            + "\"isIndustryAcademic\":true,\"industryPartners\":\"某企业\","
            + "\"introduction\":\"<p>微专业介绍</p>\","
            + "\"marketDemandAnalysis\":\"<p>市场需求分析</p>\","
            + "\"specialtyOverview\":\"<p>专业概述</p>\","
            + "\"curriculumDesign\":\"<p>课程设计</p>\","
            + "\"constructionGuarantee\":\"<p>建设保障</p>\","
            + "\"courses\":[{\"moduleName\":\"基础\",\"courseName\":\"整理学\",\"hours\":32,\"credits\":2,\"semester\":\"第1学期\"}],"
            + "\"teamMembers\":[{\"name\":\"李教授\",\"age\":45,\"title\":\"教授\",\"organization\":\"测试大学\",\"profession\":\"教育\"}],"
            + "\"signatures\":[{\"signLevel\":\"LEAD\",\"opinionText\":\"同意\",\"signature\":{\"type\":\"TEXT\",\"text\":\"李\"},\"signDate\":\"2026.9.1\"}]"
            + "}";

        mockMvc.perform(put("/api/storage-applications/{id}", proposalId)
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullForm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 3. preview - 预览数据
        mockMvc.perform(get("/api/storage-applications/{id}/preview", proposalId)
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("测试大学"));

        // 4. submit - 提交审核
        mockMvc.perform(post("/api/storage-applications/{id}/submit", proposalId)
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 5. 验证DB状态
        JdbcTemplate jdbc = new JdbcTemplate(applicationContext.getBean(javax.sql.DataSource.class));
        String dbStatus = jdbc.queryForObject(
            "SELECT status FROM micro_specialty_proposals WHERE id = ?", 
            String.class, proposalId);
        assertEquals("PENDING_REVIEW", dbStatus, "提交后状态应为 PENDING_REVIEW");
    }

    @Test
    @DisplayName("表单校验: 缺少必填项应返回19003")
    void testValidationOnSubmit() throws Exception {
        MvcResult initResult = mockMvc.perform(post("/api/storage-applications/init")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        Number pid = JsonPath.read(initResult.getResponse().getContentAsString(), "$.data");

        // 保存最少表单数据（title 必须非空通过 @Valid 校验，但缺少其他必填项）
        String minimalForm = "{"
            + "\"title\":\"测试大学\","
            + "\"microSpecialtyName\":\"\","
            + "\"leadName\":\"\","
            + "\"contactPhone\":\"13800138000\","
            + "\"enrollmentQuota\":50,"
            + "\"classSize\":25"
            + "}";
        mockMvc.perform(put("/api/storage-applications/{id}", pid.longValue())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalForm))
                .andExpect(status().isOk());

        // 提交→应失败（缺少课程、团队、签字等必填内容）
        mockMvc.perform(post("/api/storage-applications/{id}/submit", pid.longValue())
                .header("Authorization", "Bearer " + teacherToken))
                // SA_FORM_INCOMPLETE → http 400, code 19003
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(19003));
    }

    @Test
    @DisplayName("权限: 未登录用户无法创建草稿")
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(post("/api/storage-applications/init"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("自动保存: 非编辑状态应跳过")
    void testAutoSaveSkippedOnNonDraft() throws Exception {
        // 先创建一个DRAFT，提交它
        MvcResult initResult = mockMvc.perform(post("/api/storage-applications/init")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        Number pid = JsonPath.read(initResult.getResponse().getContentAsString(), "$.data");
        Long proposalId = pid.longValue();

        String form = "{\"title\":\"测试\",\"microSpecialtyName\":\"微专业\",\"leadName\":\"李\",\"contactPhone\":\"13800138000\",\"applyDate\":\"2026.9.1\",\"type\":\"急需紧缺型\",\"targetAudience\":\"本科\",\"totalCredits\":12,\"courseCount\":4,\"enrollmentQuota\":50,\"classSize\":25,\"introduction\":\"<p>t</p>\",\"marketDemandAnalysis\":\"<p>m</p>\",\"specialtyOverview\":\"<p>s</p>\",\"curriculumDesign\":\"<p>c</p>\",\"constructionGuarantee\":\"<p>g</p>\",\"courses\":[{\"moduleName\":\"M\",\"courseName\":\"C\",\"hours\":32,\"credits\":2,\"semester\":\"S1\"}],\"teamMembers\":[{\"name\":\"N\",\"age\":30,\"title\":\"T\",\"organization\":\"O\",\"profession\":\"P\"}],\"signatures\":[{\"signLevel\":\"LEAD\",\"opinionText\":\"OK\",\"signature\":{\"type\":\"TEXT\",\"text\":\"L\"},\"signDate\":\"2026.9.1\"}]}";
        mockMvc.perform(put("/api/storage-applications/{id}", proposalId)
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON).content(form))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/storage-applications/{id}/submit", proposalId)
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        // autoSave 应该静默跳过（不抛异常）
        mockMvc.perform(patch("/api/storage-applications/{id}/auto-save", proposalId)
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"已被提交后的修改\"}"))
                .andExpect(status().isOk());
    }
}

package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P1 Stage 1 集成测试
 *
 * 交叉审查 P3-2:Stage 1 字段端到端可读写验证
 *
 * 覆盖:
 * - Course 创建/读取(hid, totalHours, teachingPhilosophy, learningMode)
 * - Chapter 创建/读取(no, anchorPoint, coreQuestion, chapterHours)
 * - Section 创建/读取(no, learningObjectives, coursewareType, audioStrategy)
 */
public class P1Stage1IntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Course 接受 P1 字段(hid/totalHours/teachingPhilosophy) 并返回")
    void course_P1Fields_RoundTrip() throws Exception {
        String body = """
                {
                  "title": "P1测试课程",
                  "categoryId": 1,
                  "hid": "p1-test-course-001",
                  "totalHours": 48,
                  "totalWeeks": 16,
                  "learningMode": "online-self-study",
                  "evaluationScheme": "课后小测 20% + 期末项目 30%",
                  "teachingPhilosophy": ["锚定情境", "BOPPPS", "建构主义"]
                }
                """;
        // 创建
        String createResp = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hid").value("p1-test-course-001"))
                .andExpect(jsonPath("$.data.totalHours").value(48))
                .andExpect(jsonPath("$.data.totalWeeks").value(16))
                .andExpect(jsonPath("$.data.learningMode").value("online-self-study"))
                .andExpect(jsonPath("$.data.teachingPhilosophy[0]").value("锚定情境"))
                .andExpect(jsonPath("$.data.teachingPhilosophy[2]").value("建构主义"))
                .andReturn().getResponse().getContentAsString();

        Long courseId = Long.valueOf(JsonPath.read(createResp, "$.data.id").toString());

        // 读取(交叉审查 P1-1:VO 必须包含新字段)
        mockMvc.perform(get("/api/courses/" + courseId)
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hid").value("p1-test-course-001"))
                .andExpect(jsonPath("$.data.totalHours").value(48))
                .andExpect(jsonPath("$.data.evaluationScheme").value("课后小测 20% + 期末项目 30%"));
    }

    @Test
    @DisplayName("Course 拒绝非法 learningMode(枚举校验)")
    void course_InvalidLearningMode_Rejected() throws Exception {
        String body = """
                {
                  "title": "P1非法learningMode测试",
                  "categoryId": 1,
                  "learningMode": "INVALID-MODE"
                }
                """;
        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chapter 接受 P1 字段(no/anchorPoint/coreQuestion/chapterHours)")
    void chapter_P1Fields_RoundTrip() throws Exception {
        // 先创建一个 course + chapter(用最简单字段)
        String courseBody = """
                {"title": "P1 chapter测试课程", "categoryId": 1}
                """;
        String courseResp = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courseBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long courseId = Long.valueOf(JsonPath.read(courseResp, "$.data.id").toString());

        String chapterBody = """
                {
                  "courseId": %d,
                  "title": "P1测试章",
                  "sortOrder": 1,
                  "no": 1,
                  "anchorPoint": "小明接到任务:用 AI 工具跑销售数据",
                  "coreQuestion": "AI 真的能帮我干活吗?",
                  "chapterHours": 6
                }
                """.formatted(courseId);
        String chapterResp = mockMvc.perform(post("/api/chapters")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chapterBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value(1))
                .andExpect(jsonPath("$.data.chapterHours").value(6))
                .andExpect(jsonPath("$.data.anchorPoint").value("小明接到任务:用 AI 工具跑销售数据"))
                .andReturn().getResponse().getContentAsString();
        Long chapterId = Long.valueOf(JsonPath.read(chapterResp, "$.data.id").toString());

        // 读取
        mockMvc.perform(get("/api/chapters/" + chapterId)
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value(1))
                .andExpect(jsonPath("$.data.coreQuestion").value("AI 真的能帮我干活吗?"));
    }

    @Test
    @DisplayName("Section 接受 P1 字段(no/learningObjectives/coursewareType/audioStrategy)")
    void section_P1Fields_RoundTrip() throws Exception {
        // 先创建 course + chapter
        String courseResp = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"P1 section测试课程\",\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long courseId = Long.valueOf(JsonPath.read(courseResp, "$.data.id").toString());

        String chapterResp = mockMvc.perform(post("/api/chapters")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"title":"P1 section测试章","sortOrder":1}
                                """.formatted(courseId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long chapterId = Long.valueOf(JsonPath.read(chapterResp, "$.data.id").toString());

        // 创建 section 带 P1 字段
        String sectionBody = """
                {
                  "title": "P1测试节",
                  "sectionType": "VIDEO",
                  "no": "1.1",
                  "learningObjectives": ["理解 AI 提效来源", "识别 24 倍效率分解"],
                  "coursewareType": "HTML",
                  "audioStrategy": "15-segment"
                }
                """;
        String sectionResp = mockMvc.perform(post("/api/courses/" + courseId + "/chapters/" + chapterId + "/sections")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value("1.1"))
                .andExpect(jsonPath("$.data.coursewareType").value("HTML"))
                .andExpect(jsonPath("$.data.audioStrategy").value("15-segment"))
                .andExpect(jsonPath("$.data.learningObjectives[0]").value("理解 AI 提效来源"))
                .andExpect(jsonPath("$.data.learningObjectives[1]").value("识别 24 倍效率分解"))
                .andReturn().getResponse().getContentAsString();
        Long sectionId = Long.valueOf(JsonPath.read(sectionResp, "$.data.id").toString());

        // 读取验证 VO 包含新字段(交叉审查 P1-1)
        mockMvc.perform(get("/api/courses/" + courseId + "/chapters/" + chapterId + "/sections/" + sectionId)
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value("1.1"))
                .andExpect(jsonPath("$.data.anchorScenarioStep").doesNotExist());  // 没传 → null
    }

    @Test
    @DisplayName("Section 拒绝非法 coursewareType")
    void section_InvalidCoursewareType_Rejected() throws Exception {
        // setup
        String courseResp = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"P1非法课程\",\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long courseId = Long.valueOf(JsonPath.read(courseResp, "$.data.id").toString());

        String chapterResp = mockMvc.perform(post("/api/chapters")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseId":%d,"title":"P1非法章","sortOrder":1}
                                """.formatted(courseId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long chapterId = Long.valueOf(JsonPath.read(chapterResp, "$.data.id").toString());

        // 非法 coursewareType
        String body = """
                {"title":"P1非法课件类型","sectionType":"VIDEO","coursewareType":"INVALID"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/chapters/" + chapterId + "/sections")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
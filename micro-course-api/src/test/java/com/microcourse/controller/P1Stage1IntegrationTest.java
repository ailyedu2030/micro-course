package com.microcourse.controller;

import com.jayway.jsonpath.JsonPath;
import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * 交叉审查 P3 修复:加 @Transactional 让所有 test 写入的 DB 数据自动 rollback,
 * 避免污染其他共享 context 测试(如 V182SectionMigrationTest)的 "sections >= chapters"
 * 断言。本类创建了 chapters 但未对应 sections,如果不回滚会失败。
 *
 * 注: 早期版本用 @DirtiesContext(AFTER_CLASS) + @Sql 清理,但 DirtiesContext
 * 触发时机晚于 @Sql,且 context 重建不清理 DB 数据,最终改用 @Transactional。
 */
@Transactional
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

        // 读取(交叉审查 P1-C 第二轮:GET 路径必须也含 P1 字段)
        mockMvc.perform(get("/api/chapters/" + chapterId)
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value(1))
                .andExpect(jsonPath("$.data.chapterHours").value(6))
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

    // ===== P1 Stage 2: quiz/task/reflection =====

    @Test
    @DisplayName("POST /sections/{sid}/quizzes 创建自测题成功")
    void quiz_Create_Success() throws Exception {
        Long courseId = createCourse();
        Long chId = createChapter(courseId);
        Long sectionId = createSection(courseId, chId);

        String body = """
                {
                  "slide": 3,
                  "prompt": "AI 提效的主要来源是什么？",
                  "options": ["A. 算力", "B. 工作流重组"],
                  "correctIndex": 0,
                  "explanation": "正确选项是 B"
                }
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/sections/" + sectionId + "/quizzes")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slide").value(3))
                .andExpect(jsonPath("$.data.correctIndex").value(0))
                .andExpect(jsonPath("$.data.options[0]").value("A. 算力"))
                .andExpect(jsonPath("$.data.options[1]").value("B. 工作流重组"));
    }

    @Test
    @DisplayName("POST /sections/{sid}/tasks 创建截图任务成功")
    void task_Create_Success() throws Exception {
        Long courseId = createCourse();
        Long chId = createChapter(courseId);
        Long sectionId = createSection(courseId, chId);

        String body = """
                {"slide": 12, "description": "用 AI 工具跑本周数据，截图上传"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/sections/" + sectionId + "/tasks")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slide").value(12))
                .andExpect(jsonPath("$.data.description").value("用 AI 工具跑本周数据，截图上传"));
    }

    @Test
    @DisplayName("POST /sections/{sid}/reflections 创建反思日志成功")
    void reflection_Create_Success() throws Exception {
        Long courseId = createCourse();
        Long chId = createChapter(courseId);
        Long sectionId = createSection(courseId, chId);

        String body = """
                {"template": "200 字反思：本周 AI 如何改变了你的工作？"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/sections/" + sectionId + "/reflections")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.template").value("200 字反思：本周 AI 如何改变了你的工作？"));
    }

    @Test
    @DisplayName("POST /sections/{sid}/quizzes 学生无权限")
    void quiz_StudentForbidden() throws Exception {
        mockMvc.perform(post("/api/courses/1/sections/1/quizzes")
                        .header("Authorization", "Bearer " + loginAs("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slide\":1,\"prompt\":\"p\",\"options\":[\"A\",\"B\"],\"correctIndex\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /sections/{sid}/quizzes 非法选项(小于2)返回400")
    void quiz_InvalidOptions_Rejected() throws Exception {
        mockMvc.perform(post("/api/courses/1/sections/1/quizzes")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slide\":1,\"prompt\":\"p\",\"options\":[\"A\"],\"correctIndex\":0}"))
                .andExpect(status().isBadRequest());
    }

    // ===== P1 Stage 3: training + final-project =====

    @Test
    @DisplayName("POST /courses/{cid}/trainings 创建实训成功")
    void training_Create_Success() throws Exception {
        Long courseId = createCourse();
        String body = """
                {"no": 1, "chapter": "第 3 章后", "title": "数据清洗实战", "hours": 2, "submissionForm": "清洗报告+截图"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/trainings")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.no").value(1))
                .andExpect(jsonPath("$.data.hours").value(2));
    }

    @Test
    @DisplayName("POST /courses/{cid}/final-project 创建期末项目成功")
    void finalProject_Create_Success() throws Exception {
        Long courseId = createCourse();
        String body = """
                {"title": "AI 工具综合应用", "phases": ["选题","中期","终期"], "finalSubmissionForm": "完整报告+PPT"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/final-project")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("AI 工具综合应用"))
                .andExpect(jsonPath("$.data.phases[0]").value("选题"))
                .andExpect(jsonPath("$.data.phases[1]").value("中期"));
    }

    @Test
    @DisplayName("POST /courses/{cid}/trainings 学生无权限")
    void training_StudentForbidden() throws Exception {
        String body = """
                {"no": 1, "title": "x", "hours": 2}
                """;
        mockMvc.perform(post("/api/courses/1/trainings")
                        .header("Authorization", "Bearer " + loginAs("student", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /courses/{cid}/trainings 缺必填字段返回400")
    void training_MissingFields_Rejected() throws Exception {
        mockMvc.perform(post("/api/courses/1/trainings")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"no\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /courses/{cid}/trainings 重复序号返回400")
    void training_DuplicateNo_Rejected() throws Exception {
        Long courseId = createCourse();
        String body = """
                {"no": 1, "title": "实训1", "hours": 2}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/trainings")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/trainings")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /courses/{cid}/final-project 重复创建返回400")
    void finalProject_Duplicate_Rejected() throws Exception {
        Long courseId = createCourse();
        String body = """
                {"title": "期末项目"}
                """;
        mockMvc.perform(post("/api/courses/" + courseId + "/final-project")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/courses/" + courseId + "/final-project")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // helpers
    private Long createCourse() throws Exception {
        String resp = mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"P1测试课程-stage2\",\"categoryId\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(JsonPath.read(resp, "$.data.id").toString());
    }

    private Long createChapter(Long courseId) throws Exception {
        String resp = mockMvc.perform(post("/api/chapters")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":%d,\"title\":\"P1测试章-stage2\",\"sortOrder\":1}".formatted(courseId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(JsonPath.read(resp, "$.data.id").toString());
    }

    private Long createSection(Long courseId, Long chapterId) throws Exception {
        String resp = mockMvc.perform(post("/api/courses/" + courseId + "/chapters/" + chapterId + "/sections")
                        .header("Authorization", "Bearer " + loginAs("p0_teacher", "student123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"P1测试节-stage2\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(JsonPath.read(resp, "$.data.id").toString());
    }
}
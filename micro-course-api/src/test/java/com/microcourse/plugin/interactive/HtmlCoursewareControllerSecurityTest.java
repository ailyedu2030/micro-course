package com.microcourse.plugin.interactive;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-2 · HtmlCoursewareController 对象级授权（IDOR）修复回归测试。
 *
 * <p>修复前: 读端点（getUnit / getUnitBySection）无任何权限控制，任意登录用户可
 * 凭自增 unitId 读取任意课程课件内容；写端点仅 {@code @PreAuthorize} 角色限制，
 * 任意 TEACHER 可跨课程篡改 / 删除 HTML 课件、消耗他人 TTS 额度。</p>
 *
 * <p>修复后: 写方法由 {@link com.microcourse.web.interceptor.CourseAccessInterceptor}
 * 统一校验路径 courseId owner；读端点（GET）由 Controller 内 {@code verifyUnitOwner}
 * / {@code verifySectionUnitOwner} 显式校验 —— 关键校验 unitId 必须属于 courseId
 * （SlideHtmlUnit.courseId 字段）。任一越权 → 403 NO_PERMISSION。</p>
 *
 * <p>种子依赖 p0-seed.sql: p0_teacher(id=6, 课程A owner) / invite_teacher(id=22, 课程B owner)
 * / student(id=7) / admin(id=1)，密码 student123（admin 为 admin123）。</p>
 */
@DisplayName("P0-2 HTML 课件 Controller 对象级授权 (IDOR)")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class HtmlCoursewareControllerSecurityTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private Long courseA;
    private Long sectionA;
    private Long unitA;
    private Long segmentScriptA;

    @BeforeEach
    void setUp() {
        courseA = insertCourse(6L, "G1-P0-2-HTML课程A");
        Long chapterA = insertChapter(courseA);
        sectionA = insertSection(courseA, chapterA);
        unitA = insertHtmlUnit(courseA, chapterA, sectionA);
        segmentScriptA = insertSegmentScript(unitA);
    }

    @AfterEach
    void cleanup() {
        if (segmentScriptA != null) try { jdbc.update("DELETE FROM slide_html_segment_scripts WHERE id = ?", segmentScriptA); } catch (Exception ignored) {}
        if (unitA != null) try { jdbc.update("DELETE FROM slide_html_units WHERE id = ?", unitA); } catch (Exception ignored) {}
        if (sectionA != null) try { jdbc.update("DELETE FROM course_sections WHERE id = ?", sectionA); } catch (Exception ignored) {}
        if (courseA != null) {
            try { jdbc.update("DELETE FROM course_chapters WHERE course_id = ?", courseA); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", courseA); } catch (Exception ignored) {}
        }
    }

    // ========== 场景 1: owner TEACHER 操作自己课程的 HTML 课件 → 成功 ==========

    @Test
    @DisplayName("owner TEACHER 读取自己课程的 unit → 200")
    void ownerTeacherCanGetOwnUnit() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(unitA));
    }

    @Test
    @DisplayName("owner TEACHER 按 section 读取自己课程的 unit → 200")
    void ownerTeacherCanGetUnitBySection() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/sections/{sectionId}/unit", courseA, sectionA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("owner TEACHER 更新自己课程的 unit → 200")
    void ownerTeacherCanUpdateOwnUnit() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("p0_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"G1-更新标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 场景 2: 其他 TEACHER 越权访问他人课程 HTML 课件 → 403 ==========

    @Test
    @DisplayName("TEACHER B 读取 TEACHER A 课程的 unit（读端点 IDOR）→ 403")
    void otherTeacherDeniedGetForeignUnit() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 按 section 读取 TEACHER A 课程的 unit → 403")
    void otherTeacherDeniedGetForeignUnitBySection() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/sections/{sectionId}/unit", courseA, sectionA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 更新 TEACHER A 课程的 unit → 403")
    void otherTeacherDeniedUpdateForeignUnit() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 删除 TEACHER A 课程的 unit → 403")
    void otherTeacherDeniedDeleteForeignUnit() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 修改 TEACHER A 课程的 segment script → 403")
    void otherTeacherDeniedSaveForeignSegmentScript() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}/segments/{idx}", courseA, unitA, 1)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scriptText\":\"越权讲述稿\",\"voice\":\"xiaoyan\",\"ttsModel\":\"default\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 对 TEACHER A 课程的 segment 触发 TTS 计费 → 403（防消耗他人额度）")
    void otherTeacherDeniedGenerateSegmentAudioConsumingForeignTts() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/html/segments/{scriptId}/audios", courseA, segmentScriptA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voice\":\"xiaoyan\",\"model\":\"default\",\"ttsParams\":\"{}\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 在 TEACHER A 课程的 section 创建 unit → 403")
    void otherTeacherDeniedCreateUnitInForeignSection() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/html/sections/{sectionId}/unit", courseA, sectionA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"htmlContent\":\"<p>越权创建</p>\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // ========== 场景 3: STUDENT 调用写端点 → 403 ==========

    @Test
    @DisplayName("STUDENT 更新他人课程 unit → 403")
    void studentDeniedWriteEndpoint() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearer("student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"学生越权\"}"))
                .andExpect(status().isForbidden());
    }

    // ========== 场景 4: ADMIN 操作任意课程 → 成功 ==========

    @Test
    @DisplayName("ADMIN 读取任意课程 unit → 200")
    void adminCanGetForeignUnit() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("ADMIN 更新任意课程 unit → 200")
    void adminCanUpdateForeignUnit() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}", courseA, unitA)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"ADMIN更新\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 场景 5: 不存在的 courseId → 404 ==========

    @Test
    @DisplayName("不存在的 courseId（写方法，Interceptor 兜底）→ 404 COURSE_NOT_FOUND")
    void nonexistentCourseReturns404OnWrite() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/html/units/{unitId}", 999_999_999L, unitA)
                        .header("Authorization", bearer("p0_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));
    }

    @Test
    @DisplayName("不存在的 courseId + unitId（读方法，Controller 校验）→ 404")
    void nonexistentResourceReturns404OnRead() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/html/units/{unitId}", 999_999_999L, 888_888_888L)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isNotFound());
    }

    // ========== fixtures ==========

    private String bearer(String username) throws Exception {
        return "Bearer " + loginAs(username, P0_PASSWORD);
    }

    private Long insertCourse(Long teacherId, String title) {
        return jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) "
                        + "VALUES (?, 1, ?, 4, TRUE, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class, title, teacherId);
    }

    private Long insertChapter(Long courseId) {
        return jdbc.queryForObject(
                "INSERT INTO course_chapters(course_id, title, sort_order, version, created_at, updated_at) "
                        + "VALUES (?, 'G1章节', 0, 0, now(), now()) RETURNING id",
                Long.class, courseId);
    }

    private Long insertSection(Long courseId, Long chapterId) {
        return jdbc.queryForObject(
                "INSERT INTO course_sections(chapter_id, course_id, title, section_type, sort_order, duration, visible, version, created_at, updated_at) "
                        + "VALUES (?, ?, 'G1课时', 'INTERACTIVE', 0, 0, TRUE, 0, now(), now()) RETURNING id",
                Long.class, chapterId, courseId);
    }

    private Long insertHtmlUnit(Long courseId, Long chapterId, Long sectionId) {
        return jdbc.queryForObject(
                "INSERT INTO slide_html_units(course_id, chapter_id, section_id, slide_id, file_uuid, "
                        + "html_content, html_sanitized, file_size_bytes, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, ?, '<p>G1测试</p>', '<p>G1测试</p>', 20, now(), now()) RETURNING id",
                Long.class, courseId, chapterId, sectionId,
                java.util.UUID.randomUUID().toString().replace("-", ""));
    }

    private Long insertSegmentScript(Long unitId) {
        return jdbc.queryForObject(
                "INSERT INTO slide_html_segment_scripts(html_unit_id, segment_index, script_text, script_version, "
                        + "is_active, created_at, created_by, updated_at) "
                        + "VALUES (?, 1, 'G1测试段稿', 1, TRUE, now(), 6, now()) RETURNING id",
                Long.class, unitId);
    }
}

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
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-1 · PptCoursewareController 对象级授权（IDOR）修复回归测试。
 *
 * <p>修复前: 所有写端点仅 {@code @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")}，
 * 任意 TEACHER 可凭自增 ID 枚举/篡改他人课程的 pageId/scriptId/flowId、
 * 跨课程修改/删除课件页、消耗他人 TTS 额度（generateAudio 触发计费）。</p>
 *
 * <p>修复后: 双层防线 —— {@link com.microcourse.web.interceptor.CourseAccessInterceptor}
 * 统一校验路径 courseId owner（写方法），Service 层 verifyPageOwner/verifyScriptOwner/
 * verifyFlowOwner/verifySectionOwner 逐端点校验子资源归属。任一越权 → 403 NO_PERMISSION。</p>
 *
 * <p>种子依赖 p0-seed.sql: p0_teacher(id=6, 课程A owner) / invite_teacher(id=22, 课程B owner)
 * / student(id=7) / admin(id=1)，密码 student123（admin 为 admin123）。</p>
 */
@DisplayName("P0-1 PPT 课件 Controller 对象级授权 (IDOR)")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class PptCoursewareSecurityTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    // 课程 A 归 p0_teacher(6)，课程 B 归 invite_teacher(22)
    private Long courseA;
    private Long chapterA;
    private Long sectionA;
    private Long pageA;
    private Long scriptA;
    private Long flowA;

    @BeforeEach
    void setUp() {
        courseA = insertCourse(6L, "G1-P0-1-课程A");
        chapterA = insertChapter(courseA);
        sectionA = insertSection(courseA, chapterA);
        pageA = insertPptPage(courseA, chapterA, sectionA);
        scriptA = insertPptScript(pageA);
        flowA = insertPptFlow(sectionA, pageA);
    }

    @AfterEach
    void cleanup() {
        if (flowA != null) try { jdbc.update("DELETE FROM slide_ppt_flow WHERE id = ?", flowA); } catch (Exception ignored) {}
        if (scriptA != null) try { jdbc.update("DELETE FROM slide_ppt_page_scripts WHERE id = ?", scriptA); } catch (Exception ignored) {}
        if (pageA != null) try { jdbc.update("DELETE FROM slide_ppt_pages WHERE id = ?", pageA); } catch (Exception ignored) {}
        if (sectionA != null) try { jdbc.update("DELETE FROM course_sections WHERE id = ?", sectionA); } catch (Exception ignored) {}
        if (courseA != null) {
            try { jdbc.update("DELETE FROM course_chapters WHERE course_id = ?", courseA); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM courses WHERE id = ?", courseA); } catch (Exception ignored) {}
        }
    }

    // ========== 场景 1: owner TEACHER 操作自己课程的课件 → 成功 ==========

    @Test
    @DisplayName("owner TEACHER 更新自己课程的 page → 200")
    void ownerTeacherCanUpdateOwnPage() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("p0_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("owner TEACHER 在自家 section 创建 page → 200")
    void ownerTeacherCanCreatePageInOwnSection() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/ppt/sections/{sectionId}/pages", courseA, sectionA)
                        .header("Authorization", bearer("p0_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":" + courseA + ",\"chapterId\":" + chapterA
                                + ",\"slideId\":1,\"pageTitle\":\"新页\",\"pageNumber\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 场景 2: 其他 TEACHER 操作他人课程课件 → 403 ==========

    @Test
    @DisplayName("TEACHER B 更新 TEACHER A 课程的 page → 403 NO_PERMISSION")
    void otherTeacherDeniedUpdateForeignPage() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 删除 TEACHER A 课程的 page → 403")
    void otherTeacherDeniedDeleteForeignPage() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 在 TEACHER A 课程的 section 创建 page → 403")
    void otherTeacherDeniedCreatePageInForeignSection() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/ppt/sections/{sectionId}/pages", courseA, sectionA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pageTitle\":\"越权页\",\"pageNumber\":99}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 对 TEACHER A 课程的 script 触发 TTS 计费 → 403（防消耗他人额度）")
    void otherTeacherDeniedGenerateAudioConsumingForeignTts() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/ppt/scripts/{scriptId}/audios", courseA, scriptA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voice\":\"xiaoyan\",\"model\":\"default\",\"ttsParams\":\"{}\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 删除 TEACHER A 课程的 flow（篡改跳转规则）→ 403")
    void otherTeacherDeniedDeleteForeignFlow() throws Exception {
        mockMvc.perform(delete("/api/courses/{courseId}/ppt/flows/{flowId}", courseA, flowA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("TEACHER B 修改 TEACHER A 课程的 flow → 403")
    void otherTeacherDeniedUpdateForeignFlow() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/flows/{flowId}", courseA, flowA)
                        .header("Authorization", bearer("invite_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowType\":\"NEXT\",\"priority\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    // ========== 场景 3: STUDENT 调用写端点 → 403 ==========

    @Test
    @DisplayName("STUDENT 更新他人课程 page → 403")
    void studentDeniedWriteEndpoint() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageBody()))
                .andExpect(status().isForbidden());
    }

    // ========== 场景 4: ADMIN 操作任意课程 → 成功 ==========

    @Test
    @DisplayName("ADMIN 更新任意课程 page → 200")
    void adminCanOperateForeignCourse() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("ADMIN 对任意课程 script 触发 TTS → 200")
    void adminCanGenerateAudioOnForeignCourse() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/ppt/scripts/{scriptId}/audios", courseA, scriptA)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voice\":\"xiaoyan\",\"model\":\"default\",\"ttsParams\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== D-2 回归: PPT 读端点跨教师 IDOR (Deep Audit 2026-08-09) ==========
    // 修复前: GET /ppt/sections/{sid}/pages、/ppt/pages/{pid}、/ppt/pages/{pid}/scripts/active、
    // /ppt/scripts/{sid}/audios 仅角色门禁（TEACHER/ADMIN/ACADEMIC），无 owner 校验，
    // 非 owner 教师可凭自增 ID 读取他人课程 PPT 页/讲述稿/音频。
    // 修复后: Controller 内 verifySectionOwner / verifyPageOwner / verifyScriptOwner 显式校验
    // → 越权 403 (10003)。横向扫描: listScriptHistory / listFlows 同模式一并修复。

    @Test
    @DisplayName("D-2 owner TEACHER 读取自己课程 section 的 pages 列表 → 200")
    void d2OwnerTeacherCanListOwnPages() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/sections/{sectionId}/pages", courseA, sectionA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("D-2 owner TEACHER 读取自己课程的 page → 200")
    void d2OwnerTeacherCanGetOwnPage() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("D-2 owner TEACHER 读取自己课程的 active 讲述稿 → 200")
    void d2OwnerTeacherCanGetOwnActiveScript() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}/scripts/active", courseA, pageA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("D-2 owner TEACHER 读取自己课程 script 的音频列表 → 200")
    void d2OwnerTeacherCanListOwnAudios() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/scripts/{scriptId}/audios", courseA, scriptA)
                        .header("Authorization", bearer("p0_teacher")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程 section 的 pages 列表 → 403")
    void d2OtherTeacherDeniedListForeignPages() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/sections/{sectionId}/pages", courseA, sectionA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程的 page → 403")
    void d2OtherTeacherDeniedGetForeignPage() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程的 active 讲述稿 → 403")
    void d2OtherTeacherDeniedGetForeignActiveScript() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}/scripts/active", courseA, pageA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程 script 的音频列表 → 403")
    void d2OtherTeacherDeniedListForeignAudios() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/scripts/{scriptId}/audios", courseA, scriptA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程 page 的讲述稿历史 → 403（横向扫描）")
    void d2OtherTeacherDeniedListForeignScriptHistory() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}/scripts", courseA, pageA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 其他 TEACHER 读取他人课程 section 的 flows 列表 → 403（横向扫描）")
    void d2OtherTeacherDeniedListForeignFlows() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/sections/{sectionId}/flows", courseA, sectionA)
                        .header("Authorization", bearer("invite_teacher")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10003));
    }

    @Test
    @DisplayName("D-2 ADMIN 读取任意课程 page → 200")
    void d2AdminCanGetForeignPage() throws Exception {
        mockMvc.perform(get("/api/courses/{courseId}/ppt/pages/{pageId}", courseA, pageA)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== 场景 5: 不存在的 courseId → 404 ==========

    @Test
    @DisplayName("不存在的 courseId（写方法，Interceptor 兜底）→ 404 COURSE_NOT_FOUND")
    void nonexistentCourseReturns404OnWrite() throws Exception {
        mockMvc.perform(put("/api/courses/{courseId}/ppt/pages/{pageId}", 999_999_999L, pageA)
                        .header("Authorization", bearer("p0_teacher"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pageBody()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(6001));
    }

    // ========== fixtures ==========

    private String bearer(String username) throws Exception {
        return "Bearer " + loginAs(username, P0_PASSWORD);
    }

    private String pageBody() {
        return "{\"courseId\":" + courseA + ",\"pageTitle\":\"G1-更新标题\",\"imageUrl\":\"/x.png\"}";
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

    private Long insertPptPage(Long courseId, Long chapterId, Long sectionId) {
        return jdbc.queryForObject(
                "INSERT INTO slide_ppt_pages(course_id, chapter_id, section_id, slide_id, page_number, page_title, "
                        + "has_animation, has_embedded_media, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 1, 1, 'G1测试页', FALSE, FALSE, 0, now(), now()) RETURNING id",
                Long.class, courseId, chapterId, sectionId);
    }

    private Long insertPptScript(Long pageId) {
        return jdbc.queryForObject(
                "INSERT INTO slide_ppt_page_scripts(ppt_page_id, script_text, script_version, is_active, created_at, created_by, updated_at) "
                        + "VALUES (?, 'G1测试讲述稿', 1, TRUE, now(), 6, now()) RETURNING id",
                Long.class, pageId);
    }

    private Long insertPptFlow(Long sectionId, Long pageId) {
        return jdbc.queryForObject(
                "INSERT INTO slide_ppt_flow(section_id, from_page_id, to_page_id, flow_type, priority, description, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'NEXT', 0, 'G1测试跳转', now(), now()) RETURNING id",
                Long.class, sectionId, pageId, pageId);
    }
}

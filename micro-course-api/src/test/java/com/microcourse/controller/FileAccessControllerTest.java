package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0-SEC-001 · 文件资源安全边界集成测试。
 *
 * <p>验证 WebMvcConfig 通配静态映射去除后，私有文件（slides）必须经
 * {@link FileAccessController} 对象级授权；公开文件（covers 等）无授权仍可访问。</p>
 *
 * <h3>验证场景</h3>
 * <ol>
 *   <li>非 Owner 学生访问 slides → 403 NO_PERMISSION</li>
 *   <li>课程 Owner 教师访问 slides → 200 + 正确 Content-Type</li>
 *   <li>路径穿越（{@code ..} 字符）→ 400 BAD_REQUEST_PARAM</li>
 *   <li>管理员访问 slides → 200</li>
 *   <li>未登录访问公开 covers → 200（缺失时返回内置占位图，静态资源仍放行）</li>
 * </ol>
 *
 * <p>种子（/sql/p0-seed.sql）：course 1（teacher=6）、p0_teacher(id=6)、student(id=7)。</p>
 */
@DisplayName("P0-SEC-001 文件资源安全边界")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FileAccessControllerTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** 测试用课件文件所在目录：uploads/slides/1/（相对于 user.dir） */
    private static final Path TEST_SLIDE_DIR = Paths.get("uploads/slides/1");
    private static final String TEST_SLIDE_FILENAME = "original.pptx";
    private static final Path TEST_SLIDE_PATH = TEST_SLIDE_DIR.resolve(TEST_SLIDE_FILENAME);

    @BeforeEach
    void setUp() throws Exception {
        // 清除前序测试可能遗留的 student(id=7) 选课记录
        try { jdbc.update("DELETE FROM enrollments WHERE user_id = 7"); } catch (Exception ignored) {}

        // 创建测试课件文件（模拟已上传的 PPTX）
        Files.createDirectories(TEST_SLIDE_DIR);
        Files.write(TEST_SLIDE_PATH, new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00}); // ZIP 魔数
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理测试文件
        Files.deleteIfExists(TEST_SLIDE_PATH);
        // 空目录保留，不删除
    }

    // ================================================================
    // 场景 1：非 Owner 学生 → 403
    // ================================================================

    @Test
    @DisplayName("非 Owner 学生访问课件返回 403")
    void testNonOwnerStudentGets403() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/files/slides/1/{filename}", TEST_SLIDE_FILENAME)
                        .header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // 场景 2：课程 Owner 教师 → 200
    // ================================================================

    @Test
    @DisplayName("课程 Owner 教师成功访问课件")
    void testOwnerTeacherSucceeds() throws Exception {
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        mockMvc.perform(get("/api/files/slides/1/{filename}", TEST_SLIDE_FILENAME)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    // ================================================================
    // 场景 3：路径穿越 → 400
    // ================================================================

    @Test
    @DisplayName("路径穿越（..）被拒绝返回 400")
    void testPathTraversalRejected() throws Exception {
        String token = "Bearer " + loginAs("p0_teacher", "student123");
        // 使用 URL 编码的路径穿越：%2e%2e = ..
        mockMvc.perform(get("/api/files/slides/1/%2e%2e/%2e%2e/etc/passwd")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    // ================================================================
    // 场景 4：管理员 → 200
    // ================================================================

    @Test
    @DisplayName("管理员访问任何课件返回 200")
    void testAdminCanAccessAnySlide() throws Exception {
        String token = "Bearer " + loginAs("admin", "admin123");
        mockMvc.perform(get("/api/files/slides/1/{filename}", TEST_SLIDE_FILENAME)
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    // ================================================================
    // 场景 5：未登录访问公开资源仍放行
    // ================================================================

    @Test
    @DisplayName("未登录访问公开封面路径返回 200 占位图（文件缺失）而非 401/403")
    void testPublicCoverAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/files/covers/999/nonexistent.jpg"))
                .andExpect(status().isOk()) // 文件缺失 → 200 + 内置占位图（而非 Spring Security 拦截 401/403 或静态资源 404 破图）
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "image/svg+xml; charset=utf-8"));
    }

    // ================================================================
    // 场景 6：未登录访问私有 slides 返回 401
    // ================================================================

    @Test
    @DisplayName("未登录访问私有 slides 被 Spring Security 拦截返回 401")
    void testUnauthenticatedAccessToSlidesReturns401() throws Exception {
        mockMvc.perform(get("/api/files/slides/1/{filename}", TEST_SLIDE_FILENAME))
                .andExpect(status().isUnauthorized());
    }

    // ================================================================
    // 场景 7：已选课学生 → 200
    // ================================================================

    @Test
    @DisplayName("已选课学生访问课件返回 200")
    void testEnrolledStudentSucceeds() throws Exception {
        // 先为学生创建选课记录
        jdbc.update(
                "INSERT INTO enrollments (course_id, user_id, enrollment_status, progress, completed, enrolled_at, updated_at) " +
                "VALUES (1, 7, 'APPROVED', 0, false, now(), now()) " +
                "ON CONFLICT DO NOTHING");
        String token = "Bearer " + loginAs("student", "student123");
        mockMvc.perform(get("/api/files/slides/1/{filename}", TEST_SLIDE_FILENAME)
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }
}

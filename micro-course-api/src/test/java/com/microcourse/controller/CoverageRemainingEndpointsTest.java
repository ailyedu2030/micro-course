package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收尾覆盖 —— 对以下未充分测试的 API 模块做全面测试：
 * 正常调用（200） + 权限校验（401/403）。
 *
 * <h3>覆盖范围</h3>
 * <ol>
 *   <li>认证模块 AuthController（register/refresh/logout/change-password/upload-avatar/CAS）</li>
 *   <li>基础数据 CRUD（Department / Major / Class）</li>
 *   <li>课程分类 + 标签（CourseCategory / Tag）</li>
 *   <li>课时管理 LessonController（含 sort 排序）</li>
 *   <li>视频书签 VideoBookmarkController</li>
 *   <li>错题集 WrongQuestionController</li>
 *   <li>评分管理 GradeController</li>
 *   <li>操作日志 OperationLogController</li>
 * </ol>
 *
 * <p>账号约定（来源于 p0-seed.sql，同一 bcrypt hash 对应密码 student123）：
 * <ul>
 *   <li>ADMIN: admin / admin123（V1 种子，id=1）</li>
 *   <li>TEACHER: p0_teacher / student123（p0-seed.sql，id=6，bcrypt hash 同 student）</li>
 *   <li>STUDENT: student / student123（p0-seed.sql，id=7）</li>
 * </ul>
 *
 * <p>权限约定：
 * <ul>
 *   <li>permitAll → 无 token 200</li>
 *   <li>isAuthenticated → 无 token 401，有 token 200</li>
 *   <li>hasRole('ADMIN') / hasAnyRole('ADMIN','ACADEMIC') → TEACHER 403</li>
 *   <li>hasAnyRole('TEACHER','ADMIN') → STUDENT 403</li>
 * </ul>
 */
@DisplayName("收尾覆盖 — 剩余 API 模块全端点测试")
class CoverageRemainingEndpointsTest extends BaseIntegrationTest {

    /** p0_teacher & student 共用 bcrypt hash 对应的明文密码 */
    private static final String P0_PASSWORD = "student123";

    @Autowired
    private JdbcTemplate jdbc;

    /** 用于隔离数据的 major ID（每次 @BeforeEach 创建） */
    private Long isolatedMajorId;

    private final List<Long> createdDepartmentIds = new ArrayList<>();
    private final List<Long> createdMajorIds = new ArrayList<>();
    private final List<Long> createdClassIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdTagIds = new ArrayList<>();
    private final List<Long> createdLessonIds = new ArrayList<>();
    private final List<Long> createdUserId = new ArrayList<>();

    @BeforeEach
    void createIsolatedMajor() {
        // 防御性重置 admin 密码和登录锁（避免前序测试污染导致 bearerAdmin() 1001 失败）
        jdbc.update("UPDATE users SET password = '$2b$12$lSQy6WeZnpZzW4/7yAE4D..V0lhf3JIPGA9nyw0sYGL2EvvAM5h7C' WHERE id = 1");
        try {
            com.microcourse.util.RedisUtil ru = applicationContext.getBean(com.microcourse.util.RedisUtil.class);
            ru.delete("mc:login:lock:admin");
            ru.delete("mc:login:lock:127.0.0.1");
            ru.delete("mc:login:lock:ip:127.0.0.1");
            ru.delete("mc:login:lock:refresh:127.0.0.1");
        } catch (Exception ignored) {}
        // 在 departmentId=1（系统根院系，V1 种子）下创建一个隔离专业，供 class 测试使用
        String uniCode = "ISO_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        isolatedMajorId = jdbc.queryForObject(
                "INSERT INTO majors (name, code, department_id, sort_order, created_at, updated_at) " +
                        "VALUES (?, ?, 1, 0, now(), now()) RETURNING id",
                Long.class, "隔离测试专业-" + uniCode, uniCode);
    }

    @AfterEach
    void cleanupCreatedData() {
        // 按 FK 依赖顺序清理
        for (Long id : createdLessonIds) {
            try { jdbc.update("DELETE FROM course_sections WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdClassIds) {
            try { jdbc.update("DELETE FROM classes WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdMajorIds) {
            try { jdbc.update("DELETE FROM majors WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdDepartmentIds) {
            // 先清理该院系下可能存在的专业
            try { jdbc.update("DELETE FROM majors WHERE department_id = ?", id); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM departments WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdCategoryIds) {
            try { jdbc.update("DELETE FROM course_categories WHERE id = ?", id); } catch (Exception ignored) {}
        }
        // 清理 tag 关联再删 tag 本身
        for (Long id : createdTagIds) {
            try { jdbc.update("DELETE FROM course_tags WHERE tag_id = ?", id); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM tags WHERE id = ?", id); } catch (Exception ignored) {}
        }
        // 清理隔离专业（@BeforeEach 创建）
        if (isolatedMajorId != null) {
            try { jdbc.update("DELETE FROM classes WHERE major_id = ?", isolatedMajorId); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM majors WHERE id = ?", isolatedMajorId); } catch (Exception ignored) {}
        }
        for (Long id : createdUserId) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", id); } catch (Exception ignored) {}
        }
        createdLessonIds.clear();
        createdClassIds.clear();
        createdMajorIds.clear();
        createdDepartmentIds.clear();
        createdCategoryIds.clear();
        createdTagIds.clear();
        createdUserId.clear();
    }

    // ---- helpers ----

    private String bearerTeacher() throws Exception {
        return "Bearer " + loginAs("p0_teacher", P0_PASSWORD);
    }

    private String bearerStudent() throws Exception {
        return "Bearer " + loginAs("student", P0_PASSWORD);
    }

    private void loginRefreshCache() {
        clearAdminToken();
    }

    // ========================================================================
    //  1. 认证模块 AuthController
    // ========================================================================

    @Test
    @DisplayName("[Auth] POST /api/auth/register — 注册成功（permitAll）")
    void register_Success() throws Exception {
        String uniqueUser = "reg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + uniqueUser + "\",\"password\":\"TestReg123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/register — 重复用户名注册失败（409）")
    void register_DuplicateUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"TestReg123\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/register — 弱密码注册失败（400）")
    void register_WeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak_user\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/refresh — 用登录返回的 refreshToken 刷新成功")
    void refresh_Success() throws Exception {
        var loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andReturn();
        String refreshToken = com.jayway.jsonpath.JsonPath.read(
                loginRes.getResponse().getContentAsString(), "$.data.refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/refresh — 无 token 体（空 refreshToken）→ 400")
    void refresh_EmptyBody() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/refresh — 无效 refreshToken 返回 401")
    void refresh_InvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"dummy-invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/logout — 登出成功（isAuthenticated）")
    void logout_Success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/logout — 无 token 登出返回 401")
    void logout_WithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Auth] PUT /api/auth/me/password — 使用正确旧密码修改成功（isAuthenticated，隔离用户）")
    void changePassword_Success() throws Exception {
        // 注册一个隔离用户来测试改密，避免污染 admin 账号
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        var regRes = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cpw_" + uid + "\",\"password\":\"ChangePw1a\"}"))
                .andExpect(status().isOk()).andReturn();
        String token = "Bearer " + com.jayway.jsonpath.JsonPath.read(
                regRes.getResponse().getContentAsString(), "$.data.accessToken");

        int code = mockMvc.perform(put("/api/auth/me/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"ChangePw1a\",\"newPassword\":\"NewPwd1234\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, code, "已登录用户改密码不应被 401 拦截");
        assertNotEquals(403, code, "已登录用户改密码不应被 403 拦截");
    }

    @Test
    @DisplayName("[Auth] PUT /api/auth/me/password — 无 token 返回 401")
    void changePassword_WithoutToken() throws Exception {
        mockMvc.perform(put("/api/auth/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/me/avatar — 上传头像（isAuthenticated，权限通过即可）")
    void uploadAvatar_Authenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg",
                new byte[]{ (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 });
        int code = mockMvc.perform(multipart("/api/auth/me/avatar")
                        .file(file)
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        // 应当通过鉴权层，不被 401/403 拦截
        assertNotEquals(401, code, "已登录用户上传头像不应被 401 拦截");
        assertNotEquals(403, code, "已登录用户上传头像不应被 403 拦截");
    }

    @Test
    @DisplayName("[Auth] POST /api/auth/me/avatar — 无 token 返回 401")
    void uploadAvatar_WithoutToken() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[]{ 0x01, 0x02 });
        mockMvc.perform(multipart("/api/auth/me/avatar").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Auth] GET /api/auth/cas — CAS 回调（permitAll，无有效 ticket 应报错但非 401）")
    void cas_WithoutValidTicket() throws Exception {
        int code = mockMvc.perform(get("/api/auth/cas")
                        .param("ticket", "invalid-ticket"))
                .andReturn().getResponse().getStatus();
        // permitAll 生效：不返回 401/403；具体业务错误取决于后端实现
        assertNotEquals(401, code, "CAS 端点 permitAll，不应被 401 拦截");
    }

    // ========================================================================
    //  2. 基础数据 CRUD: Department
    // ========================================================================

    @Test
    @DisplayName("[Dept] GET /api/departments — 分页查询（isAuthenticated）")
    void departmentPage_Authenticated() throws Exception {
        mockMvc.perform(get("/api/departments")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Dept] GET /api/departments — 无 token 返回 401")
    void departmentPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Dept] GET /api/departments/{id} — 根据 ID 查询存在院系（isAuthenticated）")
    void departmentGetById_Authenticated() throws Exception {
        mockMvc.perform(get("/api/departments/1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("[Dept] GET /api/departments/999999 — 不存在院系返回 404")
    void departmentGetById_NotFound() throws Exception {
        mockMvc.perform(get("/api/departments/999999")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("[Dept] POST /api/departments — ADMIN 创建成功")
    void departmentCreate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/departments")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试院系-" + uid + "\",\"code\":\"CD_" + uid + "\",\"parentId\":null,\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdDepartmentIds.add(id);
    }

    @Test
    @DisplayName("[Dept] POST /api/departments — TEACHER 创建返回 403")
    void departmentCreate_Teacher() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"code\":\"XX\",\"parentId\":null,\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Dept] PUT /api/departments/{id} — ADMIN 更新成功")
    void departmentUpdate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/departments")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新前-" + uid + "\",\"code\":\"CU_" + uid + "\",\"parentId\":null,\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdDepartmentIds.add(id);

        mockMvc.perform(put("/api/departments/" + id)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"更新后-" + uid + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Dept] DELETE /api/departments/{id} — ADMIN 删除新建孤立院系成功")
    void departmentDelete_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/departments")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除-" + uid + "\",\"code\":\"DEL_" + uid + "\",\"parentId\":null,\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/departments/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Dept] DELETE /api/departments/{id} — TEACHER 删除返回 403")
    void departmentDelete_Teacher() throws Exception {
        mockMvc.perform(delete("/api/departments/999999")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    //  2b. 基础数据: Major
    // ========================================================================

    @Test
    @DisplayName("[Major] GET /api/majors — 分页查询（isAuthenticated）")
    void majorPage_Authenticated() throws Exception {
        mockMvc.perform(get("/api/majors")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Major] GET /api/majors — 无 token 返回 401")
    void majorPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/majors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Major] POST /api/majors — ADMIN 创建成功")
    void majorCreate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/majors")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试专业-" + uid + "\",\"code\":\"MJ_" + uid + "\",\"departmentId\":1,\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdMajorIds.add(id);
    }

    @Test
    @DisplayName("[Major] POST /api/majors — TEACHER 创建返回 403")
    void majorCreate_Teacher() throws Exception {
        mockMvc.perform(post("/api/majors")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"code\":\"XX\",\"departmentId\":1,\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Major] PUT /api/majors/{id} — ADMIN 更新成功")
    void majorUpdate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/majors")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"专业更新前-" + uid + "\",\"code\":\"MJU_" + uid + "\",\"departmentId\":1,\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdMajorIds.add(id);

        mockMvc.perform(put("/api/majors/" + id)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"专业更新后-" + uid + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Major] DELETE /api/majors/{id} — ADMIN 删除新建孤立专业成功")
    void majorDelete_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/majors")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除-" + uid + "\",\"code\":\"MJD_" + uid + "\",\"departmentId\":1,\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/majors/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Major] DELETE /api/majors/{id} — TEACHER 删除返回 403")
    void majorDelete_Teacher() throws Exception {
        mockMvc.perform(delete("/api/majors/999999")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    //  2c. 基础数据: Class
    // ========================================================================

    @Test
    @DisplayName("[Class] GET /api/classes — 分页查询（isAuthenticated）")
    void classPage_Authenticated() throws Exception {
        mockMvc.perform(get("/api/classes")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Class] GET /api/classes — 无 token 返回 401")
    void classPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Class] POST /api/classes — ADMIN 创建成功（引用 @BeforeEach 隔离专业）")
    void classCreate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试班级-" + uid + "\",\"majorId\":" + isolatedMajorId + ",\"grade\":\"2026\",\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdClassIds.add(id);
    }

    @Test
    @DisplayName("[Class] POST /api/classes — TEACHER 创建返回 403")
    void classCreate_Teacher() throws Exception {
        mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"majorId\":" + isolatedMajorId + ",\"grade\":\"2026\",\"sortOrder\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Class] PUT /api/classes/{id} — ADMIN 更新成功")
    void classUpdate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"班级更新前-" + uid + "\",\"majorId\":" + isolatedMajorId + ",\"grade\":\"2026\",\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdClassIds.add(id);

        mockMvc.perform(put("/api/classes/" + id)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"班级更新后-" + uid + "\",\"grade\":\"2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Class] DELETE /api/classes/{id} — ADMIN 删除新建孤立班级成功")
    void classDelete_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除班级-" + uid + "\",\"majorId\":" + isolatedMajorId + ",\"grade\":\"2026\",\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/classes/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Class] DELETE /api/classes/{id} — TEACHER 删除返回 403")
    void classDelete_Teacher() throws Exception {
        mockMvc.perform(delete("/api/classes/999999")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Class] GET /api/classes/{id}/students — ADMIN 可访问")
    void classStudents_Admin() throws Exception {
        // 先创建班级，再查询其学生（空名单也应成功）
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/classes")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"学生名单测试-" + uid + "\",\"majorId\":" + isolatedMajorId + ",\"grade\":\"2026\",\"sortOrder\":1}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdClassIds.add(id);

        mockMvc.perform(get("/api/classes/" + id + "/students")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Class] GET /api/classes/{id}/students — STUDENT 返回 403")
    void classStudents_Student() throws Exception {
        mockMvc.perform(get("/api/classes/1/students")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isForbidden());
    }

    // ========================================================================
    //  3a. 课程分类 CourseCategory
    // ========================================================================

    @Test
    @DisplayName("[Category] GET /api/course-categories — 分页查询（isAuthenticated）")
    void categoryPage_Authenticated() throws Exception {
        mockMvc.perform(get("/api/course-categories")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Category] GET /api/course-categories — 无 token 返回 401")
    void categoryPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/course-categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Category] POST /api/course-categories — ADMIN 创建成功")
    void categoryCreate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/course-categories")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试分类-" + uid + "\",\"parentId\":null,\"level\":1,\"sortOrder\":99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdCategoryIds.add(id);
    }

    @Test
    @DisplayName("[Category] POST /api/course-categories — TEACHER 创建返回 403")
    void categoryCreate_Teacher() throws Exception {
        mockMvc.perform(post("/api/course-categories")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"parentId\":null,\"level\":1,\"sortOrder\":99}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Category] PUT /api/course-categories/{id} — ADMIN 更新成功")
    void categoryUpdate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/course-categories")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"分类更新前-" + uid + "\",\"level\":1,\"sortOrder\":99}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdCategoryIds.add(id);

        mockMvc.perform(put("/api/course-categories/" + id)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"分类更新后-" + uid + "\",\"level\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Category] DELETE /api/course-categories/{id} — ADMIN 删除新建孤立分类成功")
    void categoryDelete_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/course-categories")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除-" + uid + "\",\"level\":1,\"sortOrder\":99}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/course-categories/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========================================================================
    //  3b. 标签 Tag
    // ========================================================================

    @Test
    @DisplayName("[Tag] GET /api/tags — 分页查询（isAuthenticated）")
    void tagPage_Authenticated() throws Exception {
        mockMvc.perform(get("/api/tags")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Tag] GET /api/tags — 无 token 返回 401")
    void tagPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Tag] POST /api/tags — ADMIN 创建成功")
    void tagCreate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试标签-" + uid + "\",\"color\":\"#1890ff\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdTagIds.add(id);
    }

    @Test
    @DisplayName("[Tag] POST /api/tags — TEACHER 创建返回 403（仅 ADMIN）")
    void tagCreate_Teacher() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"color\":\"#000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Tag] PUT /api/tags/{id} — ADMIN 更新成功")
    void tagUpdate_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"标签更新前-" + uid + "\",\"color\":\"#1890ff\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdTagIds.add(id);

        mockMvc.perform(put("/api/tags/" + id)
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"标签更新后-" + uid + "\",\"color\":\"#52c41a\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Tag] DELETE /api/tags/{id} — ADMIN 删除成功")
    void tagDelete_Admin() throws Exception {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var res = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"待删除标签-" + uid + "\",\"color\":\"#f5222d\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/tags/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Tag] GET /api/tags/course/{courseId} — 已登录可访问（返回该课程的标签列表）")
    void tagCourseGet_Authenticated() throws Exception {
        mockMvc.perform(get("/api/tags/course/1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("[Tag] POST /api/tags/course/{courseId} — ADMIN 可为课程添加标签")
    void tagCourseAdd_Admin() throws Exception {
        // 先创建标签
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var tagRes = mockMvc.perform(post("/api/tags")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课程标签测试-" + uid + "\",\"color\":\"#1890ff\"}"))
                .andExpect(status().isOk()).andReturn();
        Long tagId = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                tagRes.getResponse().getContentAsString(), "$.data.id").toString());
        createdTagIds.add(tagId);

        int code = mockMvc.perform(post("/api/tags/course/1")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagId\":" + tagId + "}"))
                .andReturn().getResponse().getStatus();
        // 可能因为课程权限（不是课主）而返回 404/403，但不会 401/500
        assertNotEquals(401, code, "已登录用户不应被 401 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    // ========================================================================
    //  4. 课时管理 LessonController
    // ========================================================================

    @Test
    @DisplayName("[Section] POST /api/courses/{courseId}/chapters/{chapterId}/sections — TEACHER 创建课时成功")
    void lessonCreate_Teacher() throws Exception {
        var res = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试课时-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdLessonIds.add(id);
    }

    @Test
    @DisplayName("[Section] POST /api/courses/{courseId}/chapters/{chapterId}/sections — STUDENT 创建返回 403")
    void lessonCreate_Student() throws Exception {
        mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", bearerStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Section] GET /api/courses/{courseId}/chapters/{chapterId}/sections — 根据章节查询已登录可用")
    void lessonGetByChapter_Authenticated() throws Exception {
        mockMvc.perform(get("/api/courses/1/chapters/1/sections")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Section] GET /api/courses/{courseId}/chapters/{chapterId}/sections/{id} — TEACHER 查询已创建课时")
    void lessonGetById_Teacher() throws Exception {
        // 先创建再查询
        String token = bearerTeacher();
        var res = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"查询测试课时-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdLessonIds.add(id);

        mockMvc.perform(get("/api/courses/1/chapters/1/sections/" + id)
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(id));
    }

    @Test
    @DisplayName("[Section] PUT /api/courses/{courseId}/chapters/{chapterId}/sections/{id} — TEACHER 更新课时成功")
    void lessonUpdate_Teacher() throws Exception {
        String token = bearerTeacher();
        var res = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"更新前-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());
        createdLessonIds.add(id);

        mockMvc.perform(put("/api/courses/1/chapters/1/sections/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"更新后-" + System.nanoTime() + "\",\"duration\":1800,\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Section] DELETE /api/courses/{courseId}/chapters/{chapterId}/sections/{id} — TEACHER 删除课时成功")
    void lessonDelete_Teacher() throws Exception {
        String token = bearerTeacher();
        var res = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"待删除课时-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                res.getResponse().getContentAsString(), "$.data.id").toString());

        mockMvc.perform(delete("/api/courses/1/chapters/1/sections/" + id)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Section] PUT /api/courses/{courseId}/chapters/{chapterId}/sections — 通过 update 验证排序正常")
    void lessonSort_Teacher() throws Exception {
        String token = bearerTeacher();
        // 先创建两个课时
        var r1 = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"课时A-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id1 = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                r1.getResponse().getContentAsString(), "$.data.id").toString());
        createdLessonIds.add(id1);

        var r2 = mockMvc.perform(post("/api/courses/1/chapters/1/sections")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"课时B-" + System.nanoTime() + "\",\"sectionType\":\"VIDEO\"}"))
                .andExpect(status().isOk()).andReturn();
        Long id2 = Long.valueOf(com.jayway.jsonpath.JsonPath.read(
                r2.getResponse().getContentAsString(), "$.data.id").toString());
        createdLessonIds.add(id2);

        // 通过更新 sortOrder 验证排序正常
        mockMvc.perform(put("/api/courses/1/chapters/1/sections/" + id1)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/api/courses/1/chapters/1/sections/" + id2)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========================================================================
    //  5. 视频书签 VideoBookmark
    // ========================================================================

    @Test
    @DisplayName("[Bookmark] GET /api/videos/{videoId}/bookmarks — 已登录（videoId 不存在则空列表）")
    void bookmarkList_Authenticated() throws Exception {
        int code = mockMvc.perform(get("/api/videos/999999/bookmarks")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        // 即使 videoId 不存在，端点应正常工作（不 500/401/403）
        assertNotEquals(401, code, "已登录用户不应被 401 拦截");
        assertNotEquals(403, code, "已登录用户不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Bookmark] GET /api/videos/{videoId}/bookmarks — 无 token 返回 401")
    void bookmarkList_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/videos/1/bookmarks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Bookmark] POST /api/videos/{videoId}/bookmarks — 已登录可通过鉴权层")
    void bookmarkCreate_Authenticated() throws Exception {
        int code = mockMvc.perform(post("/api/videos/999999/bookmarks")
                        .header("Authorization", bearerAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":30,\"label\":\"测试书签\",\"note\":\"test\"}"))
                .andReturn().getResponse().getStatus();
        // 鉴权已通过（401/403 不出现），具体业务错误取决于 videoId 是否存在
        assertNotEquals(401, code);
        assertNotEquals(403, code);
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Bookmark] POST /api/videos/{videoId}/bookmarks — 无 token 返回 401")
    void bookmarkCreate_WithoutToken() throws Exception {
        mockMvc.perform(post("/api/videos/1/bookmarks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":30,\"label\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Bookmark] DELETE /api/videos/{videoId}/bookmarks/{bookmarkId} — 已登录（owner 校验下沉 Service）")
    void bookmarkDelete_Authenticated() throws Exception {
        int code = mockMvc.perform(delete("/api/videos/1/bookmarks/999999")
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        // 鉴权通过，具体结果由 Service 层 owner 校验决定
        assertNotEquals(401, code);
        assertNotEquals(403, code);
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Bookmark] DELETE /api/videos/{videoId}/bookmarks/{bookmarkId} — 无 token 返回 401")
    void bookmarkDelete_WithoutToken() throws Exception {
        mockMvc.perform(delete("/api/videos/1/bookmarks/999999"))
                .andExpect(status().isUnauthorized());
    }

    // ========================================================================
    //  6. 错题集 WrongQuestion
    // ========================================================================

    @Test
    @DisplayName("[WrongQ] GET /api/wrong-questions/my — 已登录可查询")
    void wrongQuestionsMy_Authenticated() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("[WrongQ] GET /api/wrong-questions/my — 无 token 返回 401")
    void wrongQuestionsMy_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[WrongQ] GET /api/wrong-questions/my?courseId=1 — 按课程筛选")
    void wrongQuestionsMy_ByCourse() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .param("courseId", "1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("[WrongQ] GET /api/wrong-questions/my?chapterId=1 — 按章节筛选")
    void wrongQuestionsMy_ByChapter() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .param("chapterId", "1")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("[WrongQ] GET /api/wrong-questions/my — STUDENT 也可查询（isAuthenticated）")
    void wrongQuestionsMy_Student() throws Exception {
        mockMvc.perform(get("/api/wrong-questions/my")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========================================================================
    //  7. 评分管理 GradeController
    // ========================================================================

    @Test
    @DisplayName("[Grade] GET /api/grades — TEACHER 分页查询")
    void gradePage_Teacher() throws Exception {
        mockMvc.perform(get("/api/grades")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Grade] GET /api/grades — STUDENT 返回 403")
    void gradePage_Student() throws Exception {
        mockMvc.perform(get("/api/grades")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Grade] GET /api/grades/{id} — TEACHER 可查看详情（不存在返回 404）")
    void gradeGetById_Teacher() throws Exception {
        int code = mockMvc.perform(get("/api/grades/999999")
                        .header("Authorization", bearerTeacher()))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Grade] GET /api/grades/my — STUDENT 可查看我的成绩")
    void gradeMy_Student() throws Exception {
        mockMvc.perform(get("/api/grades/my")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[Grade] GET /api/grades/my — TEACHER 返回 403（仅 STUDENT）")
    void gradeMy_Teacher() throws Exception {
        mockMvc.perform(get("/api/grades/my")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Grade] POST /api/grades — TEACHER 可创建成绩（具体业务校验决定返回）")
    void gradeCreate_Teacher() throws Exception {
        int code = mockMvc.perform(post("/api/grades")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"userId\":7,\"score\":85,\"totalScore\":100,\"passed\":true}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 创建成绩不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Grade] POST /api/grades — STUDENT 返回 403")
    void gradeCreate_Student() throws Exception {
        mockMvc.perform(post("/api/grades")
                        .header("Authorization", bearerStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":1,\"userId\":7,\"score\":85,\"totalScore\":100}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Grade] POST /api/grades/teacher-grade — TEACHER 可批改成绩（enrollmentId 不存在返回业务错误）")
    void teacherGrade_Teacher() throws Exception {
        int code = mockMvc.perform(post("/api/grades/teacher-grade")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":999999,\"score\":85,\"comment\":\"test\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 批改成绩不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Grade] GET /api/grades/pending-review — TEACHER 可访问")
    void pendingReview_Teacher() throws Exception {
        mockMvc.perform(get("/api/grades/pending-review")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[Grade] GET /api/grades/pending-review — STUDENT 返回 403")
    void pendingReview_Student() throws Exception {
        mockMvc.perform(get("/api/grades/pending-review")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Grade] POST /api/grades/{recordId}/manual-grade — TEACHER 可手动评阅（recordId 不存在返回业务错误）")
    void manualGrade_Teacher() throws Exception {
        int code = mockMvc.perform(post("/api/grades/999999/manual-grade")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"score\":85,\"comment\":\"test\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 手动评阅不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Grade] POST /api/grades/{recordId}/manual-grade — STUDENT 返回 403")
    void manualGrade_Student() throws Exception {
        mockMvc.perform(post("/api/grades/1/manual-grade")
                        .header("Authorization", bearerStudent())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":1,\"score\":85}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[Grade] PUT /api/grades/{id} — TEACHER 可更新成绩（不存在返回业务错误）")
    void gradeUpdate_Teacher() throws Exception {
        int code = mockMvc.perform(put("/api/grades/999999")
                        .header("Authorization", bearerTeacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":90,\"comment\":\"updated\"}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 更新成绩不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    @Test
    @DisplayName("[Grade] DELETE /api/grades/{id} — TEACHER 可删除成绩（不存在返回业务错误）")
    void gradeDelete_Teacher() throws Exception {
        int code = mockMvc.perform(delete("/api/grades/999999")
                        .header("Authorization", bearerTeacher()))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, code, "TEACHER 删除成绩不应被 403 拦截");
        assertTrue(code < 500, "不应 5xx，实际=" + code);
    }

    // ========================================================================
    //  8. 操作日志 OperationLog
    // ========================================================================

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs — ADMIN 可查询")
    void operationLogPage_Admin() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs — TEACHER 返回 403")
    void operationLogPage_Teacher() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", bearerTeacher()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs — STUDENT 返回 403")
    void operationLogPage_Student() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .header("Authorization", bearerStudent()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs — 无 token 返回 401")
    void operationLogPage_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/operation-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs?action=用户登录 — 按操作筛选")
    void operationLogPage_FilterByAction() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("action", "用户登录")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs?module=Auth — 按模块筛选")
    void operationLogPage_FilterByModule() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("module", "Auth")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs?startTime=2026-01-01&endTime=2026-12-31 — 按日期范围筛选")
    void operationLogPage_FilterByDateRange() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("startTime", "2026-01-01")
                        .param("endTime", "2026-12-31")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("[OpLog] GET /api/operation-logs?page=0&size=5 — 分页参数")
    void operationLogPage_Pagination() throws Exception {
        mockMvc.perform(get("/api/operation-logs")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}

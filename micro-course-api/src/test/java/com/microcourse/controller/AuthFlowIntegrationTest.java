package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;

import com.microcourse.enums.UserRole;
import com.microcourse.support.TestHelper;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Phase B-3 · 链路 1 · 登录鉴权集成测试（10 用例）。
 *
 * <p>所有断言对齐<b>实际实现行为</b>（已逐条核对 AuthServiceImpl / JwtAuthenticationFilter /
 * ErrorCode / GlobalExceptionHandler，不修改任何业务代码）：</p>
 * <ul>
 *   <li>DISABLED(status=2) / DELETED(status=3) 登录 → ErrorCode.ACCOUNT_DISABLED/DELETED → <b>HTTP 401</b>
 *       （ErrorCode 定义 httpStatus=401，非 403）。</li>
 *   <li>连续失败 ≥5 次 → ErrorCode.LOGIN_LOCKED → <b>HTTP 423</b>（Redis login:lock 限流）。</li>
 *   <li>过期 / 无效 / 黑名单 token → JwtAuthenticationFilter writeErrorResponse → <b>HTTP 401</b>。</li>
 * </ul>
 */
@DisplayName("B-3 链路1 登录鉴权")
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    /** p0-seed.sql 中 student/student123 所用 bcrypt（$2b$12$，明文 student123） */
    private static final String BCRYPT_STUDENT123 =
            "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtil redisUtil;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<String> lockedUsernames = new ArrayList<>();

    @AfterEach
    void cleanupAuth() {
        for (Long uid : createdUserIds) {
            try { jdbc.update("DELETE FROM operation_logs WHERE user_id = ?", uid); } catch (Exception ignored) {}
            try { jdbc.update("DELETE FROM users WHERE id = ?", uid); } catch (Exception ignored) {}
        }
        createdUserIds.clear();
        for (String u : lockedUsernames) {
            try { redisUtil.clearLoginFailure(u); } catch (Exception ignored) {}
        }
        lockedUsernames.clear();
    }

    private Long insertUser(String username, int status) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'STUDENT', ?, false, now(), now()) RETURNING id",
                Long.class, username, BCRYPT_STUDENT123, "授权测试用户", status);
        createdUserIds.add(id);
        return id;
    }

    private String loginJson(String u, String p) {
        return "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
    }

    // ---------------- 1 ----------------
    @Test
    @DisplayName("1·有效用户名密码 → 200 + JWT")
    void validCredentials_Returns200AndJwt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    // ---------------- 2 ----------------
    @Test
    @DisplayName("2·JWT 携带 userId/username/role/departmentId")
    void jwt_CarriesExpectedClaims() throws Exception {
        String token = TestHelper.loginAndGetToken(mockMvc, "admin", "admin123");
        assertNotNull(jwtUtil.getUserIdFromToken(token), "JWT 必须携带 userId(sub)");
        assertEquals("admin", jwtUtil.getUsernameFromToken(token), "JWT 必须携带 username");
        assertEquals(UserRole.ADMIN, jwtUtil.getRoleFromToken(token), "JWT 必须携带 role");
        // departmentId 可能为 null（admin 无院系），仅验证该 claim 可被无异常解析
        jwtUtil.getDepartmentIdFromToken(token);
    }

    // ---------------- 3 ----------------
    @Test
    @DisplayName("3·无效密码 → 401")
    void invalidPassword_Returns401() throws Exception {
        String u = "authpwd_" + UUID.randomUUID().toString().replace("-", "");
        insertUser(u, 1);
        lockedUsernames.add(u);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(u, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
    }

    // ---------------- 4 ----------------
    @Test
    @DisplayName("4·连续失败5次后账号锁定（Redis 限流 → 423）")
    void fiveFailures_LocksAccount() throws Exception {
        String u = "lock_" + UUID.randomUUID().toString().replace("-", "");
        lockedUsernames.add(u);
        redisUtil.clearLoginFailure(u);

        // 前 5 次：用户不存在 → INVALID_CREDENTIALS(401)，每次累加失败计数
        for (int i = 1; i <= 5; i++) {
            int code = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(u, "x")))
                    .andReturn().getResponse().getStatus();
            assertEquals(401, code, "第 " + i + " 次失败应返回 401");
        }
        // 第 6 次：失败计数 ≥5 → LOGIN_LOCKED(423)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(u, "x")))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value(1006));
    }

    // ---------------- 5 ----------------
    @Test
    @DisplayName("5·DISABLED 用户登录 → 401（ACCOUNT_DISABLED）")
    void disabledUser_Returns401() throws Exception {
        String u = "disabled_" + UUID.randomUUID().toString().replace("-", "");
        insertUser(u, 2); // status=2 DISABLED
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(u, "student123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1002));
    }

    // ---------------- 6 ----------------
    @Test
    @DisplayName("6·DELETED 用户登录 → 401（ACCOUNT_DELETED）")
    void deletedUser_Returns401() throws Exception {
        String u = "deleted_" + UUID.randomUUID().toString().replace("-", "");
        insertUser(u, 3); // status=3 DELETED
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(u, "student123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ---------------- 7 ----------------
    @Test
    @DisplayName("7·refreshToken 刷新 JWT → 200 + 新 accessToken")
    void refreshToken_IssuesNewJwt() throws Exception {
        var loginRes = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("admin", "admin123")))
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

    // ---------------- 8 ----------------
    @Test
    @DisplayName("8·过期 token 调用受保护 API → 401")
    void expiredToken_Returns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        long past = System.currentTimeMillis() - 60_000L;
        String expired = Jwts.builder()
                .subject("1")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(new Date(past - 60_000L))
                .expiration(new Date(past))
                .signWith(key)
                .compact();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- 9 ----------------
    @Test
    @DisplayName("9·无 token 调用受保护 API → 401")
    void noToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- 10 ----------------
    @Test
    @DisplayName("10·logout 后 token 加入黑名单，再次使用 → 401")
    void logout_BlacklistsToken() throws Exception {
        String token = loginAs("admin", "admin123");
        assertNotNull(token);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        clearAdminToken();
    }
}

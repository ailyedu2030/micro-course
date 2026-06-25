package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ApplicationContext applicationContext;

    private String cachedAdminToken;

    /**
     * 登录并返回 accessToken
     */
    protected String loginAs(String username, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
            .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new RuntimeException("Login failed: " + result.getResponse().getContentAsString());
        }
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * 获取 admin 的 Bearer token（缓存，全类只登录一次避免Redis限流）
     */
    @BeforeEach
    public void resetLoginState() {
        cachedAdminToken = null;
        try {
            applicationContext.getBean(com.microcourse.util.RedisUtil.class).delete("mc:login:lock:admin");
            applicationContext.getBean(com.microcourse.util.RedisUtil.class).delete("mc:login:lock:student");
            applicationContext.getBean(com.microcourse.util.RedisUtil.class).delete("mc:login:lock:p0_teacher");
            applicationContext.getBean(com.microcourse.service.AuthService.class).resetLoginLockout();
        } catch (Exception ignored) {}
    }

    /**
     * CI 环境性 fallback — 防御性 p0 种子 (p0_teacher id=6, student id=7) UPSERT,
     * 不依赖 @Sql(BEFORE_TEST_METHOD) 在 CI 共享 Context 下偶发不应用的问题。
     * 用 JdbcTemplate.execute 走 Spring 管理的连接,确保提交而非回滚。
     * 注意: 必须在父类 @BeforeEach 中调用,且不抛异常吞错。
     */
    @org.junit.jupiter.api.BeforeEach
    public void ensureP0SeedUsers() {
        try {
            org.springframework.jdbc.core.JdbcTemplate jdbc =
                    applicationContext.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            // p0_teacher (id=6)
            jdbc.update("UPDATE users SET password = ?::text, status = 1, role = 'TEACHER', real_name = 'P0测试教师' "
                    + "WHERE id = 6 AND (password IS NULL OR password != ?::text)",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK");
            // INSERT if not exists (separate from UPDATE)
            jdbc.update("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                    + "SELECT 6, 'p0_teacher', ?::text, 'P0测试教师', 'TEACHER', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                    + "WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 6)",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK");
            // student (id=7)
            jdbc.update("UPDATE users SET password = ?::text, status = 1, role = 'STUDENT', real_name = 'P0测试学生' "
                    + "WHERE id = 7 AND (password IS NULL OR password != ?::text)",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK");
            jdbc.update("INSERT INTO users (id, username, password, real_name, role, status, cas_bound, created_at, updated_at) "
                    + "SELECT 7, 'student', ?::text, 'P0测试学生', 'STUDENT', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                    + "WHERE NOT EXISTS (SELECT 1 FROM users WHERE id = 7)",
                    "$2b$12$8INfOluI..wPsed6wvZSsOxfoH/dzsxaXvPR5ABQffWVKyjH7gcmK");
        } catch (Exception e) {
            // 不吞错:种子恢复失败是阻塞性问题,直接抛出
            throw new RuntimeException("ensureP0SeedUsers failed: " + e.getMessage(), e);
        }
    }

    @AfterEach
    public void cleanupLoginCache() {
        cachedAdminToken = null;
    }

    protected String bearerAdmin() throws Exception {
        if (cachedAdminToken == null) {
            cachedAdminToken = loginAs("admin", "admin123");
        }
        return "Bearer " + cachedAdminToken;
    }

    /**
     * 清除缓存的 admin token（登出场景使用）
     */
    protected void clearAdminToken() {
        cachedAdminToken = null;
    }
}

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

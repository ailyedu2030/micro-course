package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

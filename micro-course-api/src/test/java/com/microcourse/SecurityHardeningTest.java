package com.microcourse;

import com.microcourse.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Round 11-4 · 安全深度强化集成测试。
 *
 * <p>覆盖五项安全加固，所有断言对齐<b>实际实现行为</b>（不修改任何业务代码）：</p>
 * <ul>
 *   <li>登录限流：连续 5 次失败后第 6 次锁定 → HTTP 423（AuthServiceImpl SEC-006 + Redis）。</li>
 *   <li>SQL 注入：course avg_rating 更新改为 {@code #{courseId}} 参数化（源码静态断言）。</li>
 *   <li>错误响应统一：畸形 JSON → 400（非 500）；不支持的 Content-Type → 415。</li>
 *   <li>上传强化：超大封面（&gt;5MB）→ 4xx；非图片内容封面 → 4xx（魔数校验）。</li>
 * </ul>
 */
@DisplayName("Round 11-4 安全深度强化")
class SecurityHardeningTest extends BaseIntegrationTest {

    @Autowired
    private RedisUtil redisUtil;

    // ---------------- 1 · 登录限流 ----------------
    @Test
    @DisplayName("1·连续 5 次登录失败后第 6 次锁定 → 423")
    void loginShouldRateLimitAfter5Failures() throws Exception {
        String u = "rl_" + UUID.randomUUID().toString().replace("-", "");
        redisUtil.clearLoginFailure(u);
        try {
            // 前 5 次：用户不存在 → INVALID_CREDENTIALS(401)，每次累加失败计数
            for (int i = 1; i <= 5; i++) {
                int code = mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginJson(u, "wrong")))
                        .andReturn().getResponse().getStatus();
                assertEquals(401, code, "第 " + i + " 次失败应返回 401");
            }
            // 第 6 次：失败计数 ≥5 → LOGIN_LOCKED(423)
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(u, "wrong")))
                    .andExpect(status().is(423))
                    .andExpect(jsonPath("$.code").value(1006));
        } finally {
            redisUtil.clearLoginFailure(u);
        }
    }

    // ---------------- 2 · SQL 注入（参数化查询）----------------
    @Test
    @DisplayName("2·avg_rating 更新使用 #{courseId} 参数化，无字符串拼接")
    void avgRatingUpdateShouldUseParameterizedQuery() throws Exception {
        String repo = readSource("src/main/java/com/microcourse/repository/CourseRepository.java");
        assertTrue(repo.contains("updateAvgRating"),
                "CourseRepository 应提供参数化 updateAvgRating 方法");
        assertTrue(repo.contains("avg_rating = (") && repo.contains("#{courseId}"),
                "avg_rating 更新必须使用 #{courseId} 预编译参数占位符");

        String impl = readSource("src/main/java/com/microcourse/service/impl/CourseReviewServiceImpl.java");
        assertFalse(impl.contains("\" + courseId"),
                "CourseReviewServiceImpl 不得字符串拼接 courseId 进 SQL");
        assertFalse(impl.contains("setSql("),
                "CourseReviewServiceImpl 不得使用 setSql 拼接动态 SQL");
    }

    // ---------------- 3 · 畸形 JSON → 400 ----------------
    @Test
    @DisplayName("3·畸形 JSON 请求体 → 400（非 500）")
    void invalidJsonShouldReturn400Not500() throws Exception {
        int code = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not valid json "))
                .andReturn().getResponse().getStatus();
        assertEquals(400, code, "畸形 JSON 应返回 400，实际=" + code);
    }

    // ---------------- 4 · 不支持的 Content-Type → 415 ----------------
    @Test
    @DisplayName("4·不支持的 Content-Type → 415")
    void unsupportedMediaTypeShouldReturn415() throws Exception {
        int code = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("username=admin"))
                .andReturn().getResponse().getStatus();
        assertEquals(415, code, "text/plain 提交给 JSON 端点应返回 415，实际=" + code);
    }

    // ---------------- 5 · 超大封面文件 → 4xx ----------------
    @Test
    @DisplayName("5·上传超大封面（>5MB）→ 4xx")
    void largeFileShouldBeRejected() throws Exception {
        byte[] big = new byte[6 * 1024 * 1024]; // 6MB，超过 Controller 5MB 限制
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", big);
        int code = mockMvc.perform(multipart("/api/videos/1/cover")
                        .file(file)
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assertTrue(code >= 400 && code < 500,
                "超大封面应被拒绝（4xx：413 或 400），实际=" + code);
    }

    // ---------------- 6 · 非图片内容封面 → 4xx ----------------
    @Test
    @DisplayName("6·非图片内容封面（魔数校验失败）→ 4xx")
    void invalidContentShouldBeRejected() throws Exception {
        // content-type 声明 image/png 但内容非 PNG/JPEG 魔数 → 服务层魔数校验拒绝
        byte[] notImage = "this-is-not-an-image-payload-for-magic-validation"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", notImage);
        int code = mockMvc.perform(multipart("/api/videos/1/cover")
                        .file(file)
                        .header("Authorization", bearerAdmin()))
                .andReturn().getResponse().getStatus();
        assertTrue(code >= 400 && code < 500,
                "非图片内容封面应被拒绝（4xx），实际=" + code);
    }

    // ---------------- helpers ----------------
    private static String loginJson(String u, String p) {
        return "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
    }

    /** 读取源码文件，兼容 CWD = 模块目录 或 项目根目录两种运行方式。 */
    private static String readSource(String relativePath) throws IOException {
        Path p1 = Path.of(relativePath);
        if (Files.exists(p1)) {
            return Files.readString(p1, StandardCharsets.UTF_8);
        }
        Path p2 = Path.of("micro-course-api", relativePath);
        if (Files.exists(p2)) {
            return Files.readString(p2, StandardCharsets.UTF_8);
        }
        throw new IllegalStateException("源文件未找到（CWD=" + System.getProperty("user.dir")
                + "）: " + relativePath);
    }
}

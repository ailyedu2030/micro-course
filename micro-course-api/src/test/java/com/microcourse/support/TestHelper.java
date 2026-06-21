package com.microcourse.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase B-3 · 5 大核心链路集成测试共用基础设施。
 *
 * <p>仅提供测试辅助能力，<b>不触碰任何业务代码</b>：</p>
 * <ul>
 *   <li>{@link #loginAndGetToken} —— 调用既有 {@code POST /api/auth/login} 获取 accessToken；</li>
 *   <li>{@link #authHeaders} —— 构造 {@code Authorization: Bearer <token>} 请求头；</li>
 *   <li>{@link #cleanDatabase} —— 按 FK 反向顺序清空 5 大链路涉及的<b>业务叶子表</b>，
 *       不清空 users / courses / course_chapters / course_categories 等种子核心表，
 *       从而保证 admin(id=1) 与 p0-seed 种子在多测试类之间始终可用（现有 PASS 测试零退化）。</li>
 * </ul>
 *
 * <p>说明：本套件的测试类均采用「按 ID 定向清理」策略（每个测试方法 @AfterEach 删除自身创建的行），
 * {@link #cleanDatabase} 作为可选的整表清理工具按任务要求提供。</p>
 */
public final class TestHelper {

    private TestHelper() {
    }

    /**
     * 调用 {@code POST /api/auth/login} 登录并返回 accessToken。
     *
     * @return JWT accessToken（不含 "Bearer " 前缀）
     * @throws IllegalStateException 登录非 200 时抛出，便于测试快速失败定位
     */
    public static String loginAndGetToken(MockMvc mockMvc, String username, String password) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("登录失败(" + result.getResponse().getStatus() + "): "
                    + result.getResponse().getContentAsString());
        }
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /**
     * 构造携带 Bearer Token 的请求头。
     */
    public static HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * 按 FK 反向顺序清空 5 大核心链路涉及的业务叶子表（保留种子核心表）。
     *
     * <p>CASCADE 处理叶子表之间的外键依赖；不在清理列表中的 users / courses / course_chapters /
     * course_categories 等种子表完整保留，避免破坏 admin 登录与 p0-seed 依赖链。</p>
     */
    public static void cleanDatabase(JdbcTemplate jdbc) {
        jdbc.execute(
                "TRUNCATE notifications, exercise_records, grades, wrong_questions, "
                        + "enrollment_histories, enrollments, exercise_questions, exercises, "
                        + "questions, learning_progress, videos RESTART IDENTITY CASCADE");
    }
}

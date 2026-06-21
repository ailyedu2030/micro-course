package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 finding CON-003 回归测试:学习进度 totalWatchTime 增量累加不丢失。
 *
 * 修复策略:ProgressUpdateRequest 增加 watchDelta 字段,
 *          ServiceImpl 用 wrapper.setSql("total_watch_time = ... + ?") 原子累加。
 *
 * RED:   修复前 — 线程 A 上报 30 秒,线程 B 上报 45 秒,最终 total=45(A 丢失)或 30(B 丢失)
 * GREEN: 修复后 — 累加 30 + 45 = 75 秒
 * BOUNDARY: watchDelta=null 或负数 → 不变更 total_watch_time
 */
@DisplayName("CON-003 学习进度增量累加")
// P0 修复：补齐 courseId 1/2 + chapterId 1/5 种子（详见 /sql/p0-seed.sql）
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class LearningProgressP0ConcurrencyTest extends BaseIntegrationTest {

    private Long progressId1;
    private Long progressId2;

    @AfterEach
    void cleanup() {
        try {
            var ds = applicationContext.getBean(javax.sql.DataSource.class);
            try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
                stmt.execute("TRUNCATE learning_progress CASCADE");
            }
        } catch (Exception ignored) {}
        progressId1 = null;
        progressId2 = null;
    }

    @Test
    @DisplayName("RED→GREEN: 并发两个 watchDelta=30 与 watchDelta=45,最终 totalWatchTime=75")
    void incrementalAdd() throws Exception {
        String token = bearerAdmin();
        progressId1 = createTestProgress(1L, 1L);
        Long progressId = progressId1;
        int before = readTotalWatchTime(token, progressId, 1L);

        int threads = 10;
        int delta = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    String body = String.format(
                        "{\"watchDelta\":%d,\"videoProgress\":50}", delta);
                    int status = mockMvc.perform(put("/api/learning-progress/progress/" + progressId)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                        .andReturn().getResponse().getStatus();
                    if (status == 200) ok.incrementAndGet();
                } catch (Exception e) { /* */ }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        int after = readTotalWatchTime(token, progressId, 1L);
        assertEquals(threads, ok.get(), "所有并发请求必须 200");
        assertEquals(before + threads * delta, after,
            "增量累加: 初始+" + (threads * delta) + " = " + (before + threads * delta));
    }

    @Test
    @DisplayName("BOUNDARY: watchDelta=0 或负数 → totalWatchTime 不变")
    void negativeDeltaIsIgnored() throws Exception {
        String token = bearerAdmin();
        // 使用与该类第一个测试不同的 course/chapter 避免 UNIQUE 冲突
        progressId2 = createTestProgress(2L, 5L);
        Long progressId = progressId2;
        int before = readTotalWatchTime(token, progressId, 2L);

        String body = "{\"watchDelta\":-100,\"videoProgress\":10}";
        mockMvc.perform(put("/api/learning-progress/progress/" + progressId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        int after = readTotalWatchTime(token, progressId, 2L);
        assertEquals(before, after, "负数增量必须被忽略");
    }

    private Long createTestProgress(Long courseId, Long chapterId) throws Exception {
        String token = bearerAdmin();
        String body = String.format("{\"userId\":1,\"courseId\":%d,\"chapterId\":%d}", courseId, chapterId);
        MvcResult res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/learning-progress/progress")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
        int status = res.getResponse().getStatus();
        if (status != 200) {
            throw new RuntimeException("Create progress failed: " + res.getResponse().getContentAsString());
        }
        Number id = JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private int readTotalWatchTime(String token, Long progressId, Long courseId) throws Exception {
        // 从 getByUserAndCourse 的列表响应中找到匹配的记录
        MvcResult res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/learning-progress/progress?courseId=" + courseId)
                    .header("Authorization", token))
            .andReturn();
        String json = res.getResponse().getContentAsString();
        // 查找匹配的 progressId 的 totalWatchTime
        int count = com.jayway.jsonpath.JsonPath.read(json, "$.data.length()");
        for (int i = 0; i < count; i++) {
            Number idNum = com.jayway.jsonpath.JsonPath.read(json, "$.data[" + i + "].id");
            if (idNum.longValue() == progressId) {
                Number twt = com.jayway.jsonpath.JsonPath.read(json, "$.data[" + i + "].totalWatchTime");
                return twt != null ? twt.intValue() : 0;
            }
        }
        return 0;
    }
}

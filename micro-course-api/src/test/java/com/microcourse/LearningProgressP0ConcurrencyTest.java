package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
class LearningProgressP0ConcurrencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("RED→GREEN: 并发两个 watchDelta=30 与 watchDelta=45,最终 totalWatchTime=75")
    void incrementalAdd() throws Exception {
        String token = bearerAdmin();
        Long progressId = createTestProgress();
        int before = readTotalWatchTime(token, progressId);

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
                    int status = mockMvc.perform(put("/api/learning-progress/" + progressId)
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

        int after = readTotalWatchTime(token, progressId);
        assertEquals(threads, ok.get(), "所有并发请求必须 200");
        assertEquals(before + threads * delta, after,
            "增量累加: 初始+" + (threads * delta) + " = " + (before + threads * delta));
    }

    @Test
    @DisplayName("BOUNDARY: watchDelta=0 或负数 → totalWatchTime 不变")
    void negativeDeltaIsIgnored() throws Exception {
        String token = bearerAdmin();
        Long progressId = createTestProgress();
        int before = readTotalWatchTime(token, progressId);

        String body = "{\"watchDelta\":-100,\"videoProgress\":10}";
        mockMvc.perform(put("/api/learning-progress/" + progressId)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk());

        int after = readTotalWatchTime(token, progressId);
        assertEquals(before, after, "负数增量必须被忽略");
    }

    private Long createTestProgress() throws Exception {
        String token = bearerAdmin();
        String body = "{\"userId\":1001,\"courseId\":2001,\"chapterId\":3001}";
        MvcResult res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/api/learning-progress")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");
    }

    private int readTotalWatchTime(String token, Long progressId) throws Exception {
        MvcResult res = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/learning-progress/" + progressId)
                    .header("Authorization", token))
            .andReturn();
        return JsonPath.read(res.getResponse().getContentAsString(), "$.data.totalWatchTime");
    }
}

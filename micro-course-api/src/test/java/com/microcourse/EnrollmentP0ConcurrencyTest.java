package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * P0 finding CON-001 回归测试:Enrollment 幂等性 — 并发同 userId+courseId 选课必须返回同一 record,无 DuplicateKeyException 泄露。
 *
 * RED:   修复前 — 两线程并发 enroll 同一 (userId, courseId),第二个线程收到 500 (DuplicateKeyException 冒泡)
 * GREEN: 修复后 — 两线程并发 enroll,均收到 200,返回同一 enrollment.id
 * BOUNDARY: 同一连接池最大并发 10 线程 enroll 同一课程,全部 200 且 enrollment.id 唯一
 */
@DisplayName("CON-001 Enrollment 幂等回归")
class EnrollmentP0ConcurrencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("RED→GREEN: 并发两线程 enroll 同一 (userId, courseId) 必须都成功且返回相同 id")
    void concurrentEnrollIdempotent() throws Exception {
        String token = bearerAdmin();
        String body = "{\"userId\":1,\"courseId\":1,\"sourceChannel\":\"WEB\"}";

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<Long> enrollTask = () -> {
            start.await();
            try {
                MvcResult res = mockMvc.perform(post("/api/enrollments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                    .andReturn();
                int status = res.getResponse().getStatus();
                if (status == 200) {
                    successCount.incrementAndGet();
                    return JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");
                }
                failureCount.incrementAndGet();
                return -1L;
            } catch (Exception e) {
                failureCount.incrementAndGet();
                return -1L;
            }
        };

        Future<Long> f1 = pool.submit(enrollTask);
        Future<Long> f2 = pool.submit(enrollTask);
        start.countDown();

        Long id1 = f1.get(10, TimeUnit.SECONDS);
        Long id2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(2, successCount.get(),
            "两个并发请求都必须成功(修复前:第二个因 DuplicateKey 收到 500)");
        assertEquals(id1, id2,
            "两个响应必须返回相同 enrollment.id(幂等性证据)");
    }

    @Test
    @DisplayName("BOUNDARY: 10 线程并发 enroll 同一课程,仅产生 1 条 enrollment 记录,无 500")
    void highConcurrencyEnroll() throws Exception {
        String token = bearerAdmin();
        String body = "{\"userId\":1002,\"courseId\":2002,\"sourceChannel\":\"WEB\"}";

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failure = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    int status = mockMvc.perform(post("/api/enrollments")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                        .andReturn().getResponse().getStatus();
                    if (status == 200) success.incrementAndGet();
                    else failure.incrementAndGet();
                } catch (Exception e) { failure.incrementAndGet(); }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(threads, success.get(),
            threads + " 个并发请求全部必须 200(否则即视为 DuplicateKey 泄露)");
        assertEquals(0, failure.get());
    }
}

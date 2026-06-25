package com.microcourse.config;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * P0 finding RES-001 + RES-002 回归测试:@Async 必须用有界线程池。
 *
 * 修复策略:新增 AsyncConfig.java,定义 taskExecutor 和 videoUploadExecutor
 *          Bean。VideoController.upload() 注入并使用 videoUploadExecutor。
 *
 * RED:   修复前 — @EnableAsync 但无 AsyncConfigurer,使用 SimpleAsyncTaskExecutor 无界
 * GREEN: 修复后 — 注入 ThreadPoolTaskExecutor,有 core/max/queue/rejectionPolicy
 * BOUNDARY: videoUploadExecutor 拒策略 = AbortPolicy,队列满后必须抛 RejectedExecutionException
 */
@DisplayName("RES-001/RES-002 异步线程池边界")
class AsyncConfigP0ResourceTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    @Qualifier("videoUploadExecutor")
    private Executor videoUploadExecutor;

    @Test
    @DisplayName("GREEN: videoUploadExecutor 是 ThreadPoolTaskExecutor,有边界")
    void executorIsBounded() {
        assertNotNull(videoUploadExecutor);
        assertTrue(videoUploadExecutor instanceof ThreadPoolTaskExecutor);
        ThreadPoolTaskExecutor tp = (ThreadPoolTaskExecutor) videoUploadExecutor;
        ThreadPoolExecutor raw = tp.getThreadPoolExecutor();
        assertTrue(raw.getCorePoolSize() > 0, "corePoolSize 必须 > 0");
        assertTrue(raw.getMaximumPoolSize() > raw.getCorePoolSize(),
            "maxPoolSize 必须 ≥ corePoolSize");
        assertTrue(raw.getQueue().remainingCapacity() < Integer.MAX_VALUE,
            "queue 容量必须有限");
    }

    @Test
    @DisplayName("GREEN: AsyncConfig Bean 提供 taskExecutor 覆盖默认 SimpleAsyncTaskExecutor")
    void defaultExecutorIsReplaced() {
        assertTrue(applicationContext.containsBean("taskExecutor"),
            "AsyncConfig.taskExecutor 必须存在");
        Executor exec = applicationContext.getBean("taskExecutor", Executor.class);
        assertNotNull(exec);
        assertFalse(exec.getClass().getSimpleName().contains("SimpleAsyncTaskExecutor"),
            "默认 SimpleAsyncTaskExecutor 必须被替换");
    }

    @Test
    @DisplayName("BOUNDARY: 提交超过队列容量+maxPoolSize 的任务,AbrotPolicy 必须抛 RejectedExecutionException")
    void rejectPolicyFires() throws Exception {
        ThreadPoolExecutor raw = ((ThreadPoolTaskExecutor) videoUploadExecutor).getThreadPoolExecutor();
        // 准备大量任务堆积
        AtomicInteger ran = new AtomicInteger(0);
        int total = raw.getMaximumPoolSize() + raw.getQueue().remainingCapacity() + 100;
        boolean rejected = false;
        try {
            for (int i = 0; i < total; i++) {
                videoUploadExecutor.execute(() -> {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    ran.incrementAndGet();
                });
            }
        } catch (RejectedExecutionException e) {
            rejected = true;
        }
        assertTrue(rejected, "队列+线程池满后必须触发拒绝策略");
    }

    @Test
    @DisplayName("GREEN: AsyncConfig 类被 Spring 加载(配置生效)")
    void asyncConfigClassLoaded() {
        assertNotNull(applicationContext.getBean(AsyncConfig.class));
    }
}

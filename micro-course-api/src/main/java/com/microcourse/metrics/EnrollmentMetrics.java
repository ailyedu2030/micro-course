package com.microcourse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 选课业务指标（生产可观测性 P0 增强）
 *
 * 暴露给 Prometheus 抓取 → /actuator/prometheus
 *
 * 关键指标:
 * - enrollment_total{result="ok|waitlist|error"}: 选课请求结果计数
 * - enrollment_duration_seconds: 选课操作耗时（含行级锁）
 * - enrollment_lock_timeout_total: 行锁超时计数
 * - enrollment_overcapacity_prevented_total: 防止超卖次数（修复有效性的实时证据）
 *
 * 告警规则示例:
 * - rate(enrollment_total{result="error"}[5m]) > 0.1 → 选课错误率告警
 * - histogram_quantile(0.99, enrollment_duration_seconds) > 2 → P99 超过 2s
 * - rate(enrollment_overcapacity_prevented_total[1m]) > 0 → 选课满员（容量监控）
 */
@Component
public class EnrollmentMetrics {

    private final Counter successCounter;
    private final Counter waitlistCounter;
    private final Counter errorCounter;
    private final Counter overcapacityPreventedCounter;
    private final Timer enrollTimer;

    public EnrollmentMetrics(MeterRegistry registry) {
        this.successCounter = Counter.builder("enrollment_total")
                .description("Total enrollment operations")
                .tag("result", "ok")
                .register(registry);
        this.waitlistCounter = Counter.builder("enrollment_total")
                .description("Total enrollment operations")
                .tag("result", "waitlist")
                .register(registry);
        this.errorCounter = Counter.builder("enrollment_total")
                .description("Total enrollment operations")
                .tag("result", "error")
                .register(registry);
        this.overcapacityPreventedCounter = Counter.builder("enrollment_overcapacity_prevented_total")
                .description("Times enrollment was blocked because course was full (P0-1 fix verification)")
                .register(registry);
        this.enrollTimer = Timer.builder("enrollment_duration_seconds")
                .description("Time to process an enrollment request (includes row lock)")
                .register(registry);
    }

    public void recordSuccess() { successCounter.increment(); }
    public void recordWaitlist() { waitlistCounter.increment(); }
    public void recordError() { errorCounter.increment(); }
    public void recordOvercapacityPrevented() { overcapacityPreventedCounter.increment(); }
    public Timer enrollTimer() { return enrollTimer; }
}

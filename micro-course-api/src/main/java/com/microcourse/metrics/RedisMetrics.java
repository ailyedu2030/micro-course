package com.microcourse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Redis 操作可观测性指标
 *
 * ERR-004 修复: Redis 降级操作必须有指标可观测，否则故障无法发现。
 *
 * 告警规则:
 * - rate(mc_redis_error_total{operation="*"}[5m]) > 0.1 → Redis 连接异常告警
 * - rate(mc_redis_login_degrade_total[1m]) > 0  → 登录保护降级告警（暴力破解风险）
 * - rate(mc_redis_token_blacklist_degrade_total[1m]) > 0 → Token 黑名单降级告警（安全降级）
 */
@Component
public class RedisMetrics {

    private final Counter loginFailureCounter;
    private final Counter loginCheckCounter;
    private final Counter loginClearCounter;
    private final Counter tokenBlacklistCounter;
    private final Counter tokenCheckCounter;
    private final Counter pingCounter;

    public RedisMetrics(MeterRegistry registry) {
        this.loginFailureCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "incr_login_failure")
                .register(registry);
        this.loginCheckCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "get_login_failure_count")
                .register(registry);
        this.loginClearCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "clear_login_failure")
                .register(registry);
        this.tokenBlacklistCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "token_blacklist")
                .register(registry);
        this.tokenCheckCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "token_blacklist_check")
                .register(registry);
        this.pingCounter = Counter.builder("mc_redis_error_total")
                .description("Redis operation errors")
                .tag("operation", "ping")
                .register(registry);
    }

    public void recordLoginFailureError() { loginFailureCounter.increment(); }
    public void recordLoginCheckError() { loginCheckCounter.increment(); }
    public void recordLoginClearError() { loginClearCounter.increment(); }
    public void recordTokenBlacklistError() { tokenBlacklistCounter.increment(); }
    public void recordTokenCheckError() { tokenCheckCounter.increment(); }
    public void recordPingError() { pingCounter.increment(); }
}

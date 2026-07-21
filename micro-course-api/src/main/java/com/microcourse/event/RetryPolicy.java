package com.microcourse.event;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * P1 plan Task 10: 5 步指数退避策略.
 * 第 0 次立即, 第 1 次立即, 之后 30s/5m/30m/2h, 第 5 次后死信.
 */
public final class RetryPolicy {

    private RetryPolicy() {}

    /**
     * 计算下一次重试时间.
     * @param attemptCount 已尝试次数 (0=首次, 1=第一次重试, ...)
     */
    public static LocalDateTime nextAttemptAt(int attemptCount) {
        long seconds = switch (attemptCount) {
            case 0 -> 0L;        // 首次立即
            case 1 -> 0L;        // 第一次重试立即
            case 2 -> 30L;       // 30s
            case 3 -> 300L;      // 5m
            case 4 -> 1800L;     // 30m
            case 5 -> 7200L;     // 2h
            default -> 86400L;   // 24h (死信复活窗口)
        };
        return LocalDateTime.now().plus(seconds, ChronoUnit.SECONDS);
    }

    /**
     * 是否应进入死信表 (5 次重试均失败).
     */
    public static boolean shouldDeadLetter(int attemptCount) {
        return attemptCount >= 5;
    }
}

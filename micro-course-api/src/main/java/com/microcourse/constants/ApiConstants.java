package com.microcourse.constants;

/**
 * 通用常量（P2-1 · 2026-08-15 全仓 magic number 常量化）。
 *
 * <p>统一收敛以下硬编码数字，避免不同类各自魔数、语义漂移：
 * <ul>
 *   <li>时间换算（ms/s/min/hour/day）</li>
 *   <li>HTTP 缓存 / Cookie 有效期（秒）</li>
 *   <li>音频采样率 / 限额</li>
 *   <li>统计兜底值</li>
 * </ul>
 *
 * @author 总工程师
 * @since 2026-08-15
 */
public final class ApiConstants {

    private ApiConstants() {
        throw new AssertionError("ApiConstants is a constants holder, never instantiate");
    }

    // ===== 时间换算（毫秒）=====
    public static final long MILLIS_PER_SECOND = 1000L;
    public static final long MILLIS_PER_MINUTE = 60 * MILLIS_PER_SECOND;
    public static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    public static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;

    // ===== HTTP 缓存 / Cookie（秒）=====
    /** HTTP 缓存 max-age 1 天（86400s） */
    public static final int HTTP_CACHE_MAX_AGE_DAY = 86400;
    /** 登录 Cookie 有效期 1 年（31536000s） */
    public static final int COOKIE_MAX_AGE_YEAR = 31536000;

    // ===== 音频 =====
    /** 默认音频采样率（TTS 生成 / 上传统一使用） */
    public static final int DEFAULT_SAMPLE_RATE = 32000;

    // ===== 统计分析 =====
    /** 准确率/成绩统计 LIMIT（毫秒内避免全表扫描） */
    public static final int STATS_LIMIT = 2000;
    /** 操作日志分页 LIMIT */
    public static final int OPERATION_LOG_LIMIT = 10000;
    /** 统计失败兜底值 */
    public static final long STATS_NA = -1L;
}

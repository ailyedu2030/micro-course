package com.microcourse.config;

/**
 * 选课功能开关 (生产可观测性 P0-3)
 *
 * 通过 application.yml 配置或环境变量 ENROLLMENT_ENABLED=false 即可秒级关停选课
 *
 * 用法:
 * - 紧急回滚: ENROLLMENT_ENABLED=false 重启 → 所有 enroll() 返回 503
 * - 灰度发布: ENROLLMENT_ENABLED=true → 全开
 * - 限流: 同 message-queue 削峰
 *
 * 默认: true（启用）
 */
public class EnrollmentFeatureFlag {

    private static volatile boolean enabled = true;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 动态读取系统属性 (无需重启)
     * 调用方式: System.getProperty("enrollment.enabled", "true")
     */
    public static boolean isDynamicallyEnabled() {
        String prop = System.getProperty("enrollment.enabled");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        String env = System.getenv("ENROLLMENT_ENABLED");
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        return enabled;
    }
}

package com.microcourse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MicroCourseApplication {

    private static final Logger log = LoggerFactory.getLogger(MicroCourseApplication.class);

    @Value("${video.sign.secret:}")
    private String videoSignSecret;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${payment.mode:mock}")
    private String paymentMode;

    public static void main(String[] args) {
        SpringApplication.run(MicroCourseApplication.class, args);
    }

    /**
     * P0-12: 生产环境禁止使用 mock 支付模式。
     * 若 active profile 包含 prod 且 payment.mode 为 mock，启动直接失败。
     * P0-4: 非 prod 环境使用 mock 模式时记录警告日志。
     */
    @jakarta.annotation.PostConstruct
    public void checkPaymentMode() {
        if ("mock".equals(paymentMode)) {
            if (activeProfile.contains("prod")) {
                throw new IllegalStateException(
                        "生产环境禁止使用 mock 支付模式，请设置 PAYMENT_MODE=real 并配置支付网关");
            }
            log.warn("[PAYMENT] 当前使用 MOCK 支付模式！支付将模拟成功，不产生真实交易。"
                    + " 生产部署前请务必切换为 real 模式。");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (videoSignSecret.isEmpty() || videoSignSecret.equals(jwtSecret)) {
            log.warn("[SECURITY] VIDEO_SIGN_SECRET 未显式配置或与 JWT_SECRET 相同！"
                    + " 建议设置独立的 VIDEO_SIGN_SECRET 环境变量，避免视频签名密钥泄露导致 JWT 可伪造");
        }
    }
}

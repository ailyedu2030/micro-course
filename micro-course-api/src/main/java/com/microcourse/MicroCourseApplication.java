package com.microcourse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
@SpringBootApplication
public class MicroCourseApplication {

    private static final Logger log = LoggerFactory.getLogger(MicroCourseApplication.class);

    @Value("${video.sign.secret:}")
    private String videoSignSecret;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    public static void main(String[] args) {
        SpringApplication.run(MicroCourseApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (videoSignSecret.isEmpty() || videoSignSecret.equals(jwtSecret)) {
            log.warn("[SECURITY] VIDEO_SIGN_SECRET 未显式配置或与 JWT_SECRET 相同！"
                    + " 建议设置独立的 VIDEO_SIGN_SECRET 环境变量，避免视频签名密钥泄露导致 JWT 可伪造");
        }
    }
}

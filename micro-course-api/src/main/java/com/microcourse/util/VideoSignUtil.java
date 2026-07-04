package com.microcourse.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 视频播放 URL 签名工具
 *
 * P1-7 修复：使用独立 video.sign.secret，不再与 JWT 密钥共用
 * 生成 JWT 格式的播放签名，payload: {"videoId":123, "exp":<epoch_seconds>}
 * 使用 HMAC-SHA256
 */
@Component
public class VideoSignUtil {

    @Value("${video.sign.secret:}")
    private String secret;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private SecretKey cachedKey;

    @PostConstruct
    void init() {
        boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
        if (secret == null || secret.isEmpty()) {
            if (isProduction) {
                // 安全: 生产环境 fail-fast,绝不兜底密钥
                throw new IllegalStateException("[SEC-004] VIDEO_SIGN_SECRET 未配置,生产环境拒绝启动");
            }
            // 仅本地开发环境兜底密钥(单元测试 / mvn spring-boot:run)
            secret = "dev-only-video-sign-secret-key-min-32-bytes-please-change";
            System.err.println("[WARN] video.sign.secret 未配置,使用本地开发兜底密钥(仅限开发环境)");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("VIDEO_SIGN_SECRET 必须至少 32 字节");
        }
        this.cachedKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成视频播放签名
     *
     * @param videoId     视频 ID
     * @param expireHours 过期小时数（默认 2）
     * @return JWT 字符串
     */
    public String generateSign(Long videoId, int expireHours) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + (long) expireHours * 3600 * 1000);

        return Jwts.builder()
                .claim("videoId", videoId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getKey())
                .compact();
    }

    /**
     * 验证视频播放签名
     *
     * @param videoId 视频 ID
     * @param token   JWT 签名 token
     * @return true if valid, false otherwise
     */
    public boolean verifySign(Long videoId, String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // 检查 videoId 匹配
            Object claimVideoId = claims.get("videoId");
            if (claimVideoId == null) {
                return false;
            }

            boolean videoIdMatch;
            if (claimVideoId instanceof Integer) {
                videoIdMatch = ((Integer) claimVideoId).longValue() == videoId;
            } else if (claimVideoId instanceof Long) {
                videoIdMatch = claimVideoId.equals(videoId);
            } else {
                videoIdMatch = false;
            }

            return videoIdMatch;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private SecretKey getKey() {
        return cachedKey;
    }
}

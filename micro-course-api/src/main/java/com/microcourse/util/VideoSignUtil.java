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
 * 依据：Phase 8 开发规范
 * 生成 JWT 格式的播放签名，payload: {"videoId":123, "exp":<epoch_seconds>}
 * 使用 HMAC-SHA256，密钥从 application.yml 的 jwt.secret 取
 * 深度审查：密钥缓存优化，避免每次调用重建 SecretKey
 */
@Component
public class VideoSignUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey cachedKey;

    @PostConstruct
    void init() {
        this.cachedKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
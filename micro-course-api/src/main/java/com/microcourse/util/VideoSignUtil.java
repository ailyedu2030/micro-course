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

    @Value("${video.sign.secret}")
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

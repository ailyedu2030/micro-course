package com.microcourse.util;

import com.microcourse.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 *
 * 依据：
 * - jjwt 0.12.3 API
 * - Phase 1 业务逻辑 §2.4：JWT Token 结构（accessToken 7 claims，refreshToken 3 claims）
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    public JwtUtil() {
    }

    /**
     * 生成 accessToken
     * claims: sub(userId), username, role, departmentId, jti(UUID), iat, exp
     */
    public String generateToken(Long userId, String username, UserRole role, Long departmentId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role.name())
                .claim("departmentId", departmentId)
                .claim("jti", jti)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成 refreshToken
     * claims: sub(userId), iat, exp
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getKey())
                .compact();
    }

    /**
     * 验证 token（签名 + 过期）
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 提取 userId
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从 token 提取 username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 token 提取 role
     */
    public UserRole getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        String roleStr = claims.get("role", String.class);
        return UserRole.valueOf(roleStr);
    }

    /**
     * 从 token 提取 departmentId（可能为 null）
     */
    public Long getDepartmentIdFromToken(String token) {
        Claims claims = getClaims(token);
        Object deptId = claims.get("departmentId");
        if (deptId == null) {
            return null;
        }
        if (deptId instanceof Integer) {
            return ((Integer) deptId).longValue();
        }
        return (Long) deptId;
    }

    /**
     * 从 token 提取 jti
     */
    public String getJtiFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("jti", String.class);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
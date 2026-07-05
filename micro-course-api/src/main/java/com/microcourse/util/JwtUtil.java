package com.microcourse.util;

import com.microcourse.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * - 深度审查：密钥缓存 + 启动时长度校验（HMAC-SHA256 至少 32 字节）
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration:7200000}")
    private Long expiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private Long refreshExpiration;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    private SecretKey cachedKey;

    public JwtUtil() {
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isEmpty()) {
            // R8 修复 P0-1: 本地开发兜底密钥（生产必须通过 JWT_SECRET 环境变量显式设置）
            // 仅用于 mvn spring-boot:run 本地启动场景，不应用于生产部署
            // SEC-003 修复: 生产环境 fail-fast，杜绝硬编码密钥泄露
            boolean isProduction = activeProfiles != null && activeProfiles.contains("prod");
            if (isProduction) {
                throw new IllegalStateException(
                        "[SECURITY] jwt.secret 未配置！生产环境必须设置 JWT_SECRET 环境变量，禁止使用兜底密钥");
            }
            secret = "dev-only-jwt-secret-key-min-32-bytes-please-change-in-prod";
            log.warn("jwt.secret 未配置，使用本地开发兜底密钥（仅限开发环境）");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足: 需要至少 32 字节 (HMAC-SHA256), 当前 " + keyBytes.length + "字节");
        }
        this.cachedKey = Keys.hmacShaKeyFor(keyBytes);
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
     * claims: sub(userId), jti(UUID), iat, exp, tokenGen
     * @param userId 用户 ID
     * @param tokenGeneration token 代数(每次登录递增,用于在 refresh 时校验旧 token 已被登录作废)
     */
    public String generateRefreshToken(Long userId, Long tokenGeneration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("jti", UUID.randomUUID().toString())
                .claim("tokenGen", tokenGeneration)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成 refreshToken(不携带 tokenGeneration,兼容旧调用方)
     * 等价于 generateRefreshToken(userId, 0L)
     */
    public String generateRefreshToken(Long userId) {
        return generateRefreshToken(userId, 0L);
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
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return false;
        } catch (io.jsonwebtoken.security.SecurityException
                | io.jsonwebtoken.MalformedJwtException
                | io.jsonwebtoken.UnsupportedJwtException
                | IllegalArgumentException e) {
            return false;
        } catch (Exception e) {
            log.warn("JWT验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证 refreshToken（签名 + 过期）
     * 异常分类模式与 validateToken 保持一致
     */
    public boolean validateRefreshToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return false;
        } catch (io.jsonwebtoken.security.SecurityException
                | io.jsonwebtoken.MalformedJwtException
                | io.jsonwebtoken.UnsupportedJwtException
                | IllegalArgumentException e) {
            return false;
        } catch (Exception e) {
            log.warn("refreshToken验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 token 提取 userId
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaims(token);
        String subject = claims.getSubject();
        if (subject == null) throw new IllegalArgumentException("Token missing subject claim");
        return Long.parseLong(subject);
    }

    /**
     * 从 token 提取 username
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("username", String.class);
    }

    /**
     * 从 token 提取 role（支持逗号分隔的多角色）
     */
    public String getRoleFromTokenAsString(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * 从 token 提取 role
     */
    public UserRole getRoleFromToken(String token) {
        Claims claims = getClaims(token);
        String roleStr = claims.get("role", String.class);
        if (roleStr == null) throw new IllegalArgumentException("Token missing role claim");
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
     * 提取 jti 而不验证 JWT 签名 — P0-S04 修复:在检查黑名单之前快速获取 jti，
     * 避免在已作废的 token 上浪费 CPU 进行签名验证。
     * 如果 token 无法解析(格式错误等),返回 null。
     */
    public String getJtiUnverified(String token) {
        try {
            Object body = Jwts.parser()
                    .build()
                    .parse(token)
                    .getBody();
            if (body instanceof io.jsonwebtoken.Claims claims) {
                return claims.get("jti", String.class);
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse JWT for jti (unverified): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 token 提取 jti
     */
    public String getJtiFromToken(String token) {
        Claims claims = getClaims(token);
        return claims.get("jti", String.class);
    }

    /**
     * 获取 token 剩余有效期（秒）
     */
    public long getExpirationRemainingSeconds(String token) {
        Claims claims = getClaims(token);
        Date exp = claims.getExpiration();
        if (exp == null) return 0;
        long remaining = (exp.getTime() - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * 从 token 提取 tokenGeneration(P0-S04 修复:登录后旧 refreshToken 失效校验)
     * 若 token 中无此 claim 或解析失败,返回 0(兼容旧 token)
     */
    public long getTokenGeneration(String token) {
        try {
            Claims claims = getClaims(token);
            Object gen = claims.get("tokenGen");
            if (gen == null) return 0L;
            if (gen instanceof Number) return ((Number) gen).longValue();
            if (gen instanceof String) return Long.parseLong((String) gen);
            return 0L;
        } catch (Exception e) {
            log.warn("Failed to get tokenGeneration: {}", e.getMessage());
            return 0L;
        }
    }

    private SecretKey getKey() {
        return cachedKey;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
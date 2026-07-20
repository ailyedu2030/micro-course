package com.microcourse.plugin.interactive.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * AudioTokenService · audio token 颁发 + 校验 (spec 4.1 / audio/)
 *
 * 设计:
 * - 32 字符 hex (UUID v4 + 字符集规范化)
 * - UK 唯一 (DB 唯一索引保障)
 * - 不暴露真实 file path, 仅 token
 *
 * 7-19 P0 防御: token 不依赖 pageNumber, 即使 pageNumber 篡改也无效.
 */
@Component
public class AudioTokenService {

    private static final Logger log = LoggerFactory.getLogger(AudioTokenService.class);

    private static final int TOKEN_LENGTH = 32;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[a-f0-9]{32}$");
    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * 生成 32 字符 hex token
     */
    public String generateToken() {
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        for (int i = 0; i < TOKEN_LENGTH; i++) {
            sb.append(HEX_CHARS[RNG.nextInt(HEX_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 校验 token 格式
     */
    public boolean isValidToken(String token) {
        if (token == null) return false;
        return TOKEN_PATTERN.matcher(token).matches();
    }

    /**
     * 校验 token 失败时记录可疑活动
     */
    public void recordInvalidToken(String token, String clientInfo) {
        log.warn("[AudioToken] INVALID token attempt: length={}, client={}",
                token == null ? "null" : token.length(), clientInfo);
    }
}
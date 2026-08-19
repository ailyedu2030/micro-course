package com.microcourse.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryptor {

    private static final Logger log = LoggerFactory.getLogger(FieldEncryptor.class);
    private static final String ENC_PREFIX = "ENC:";

    private final TextEncryptor encryptor;

    public FieldEncryptor(
            @Value("${app.security.field-encryption-key:}") String password,
            @Value("${app.security.field-encryption-salt:}") String salt) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.field-encryption-key 未配置。请设置环境变量 APP_SECURITY_FIELD_ENCRYPTION_KEY（>= 32 字符）");
        }
        if (password.length() < 32) {
            throw new IllegalArgumentException(
                    "app.security.field-encryption-key 长度不足 32 字符，当前长度: " + password.length());
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException(
                    "app.security.field-encryption-salt 未配置。请设置环境变量 APP_SECURITY_FIELD_ENCRYPTION_SALT（>= 16 字符）");
        }
        this.encryptor = Encryptors.delux(password, salt);
    }

    @PostConstruct
    void init() {
        log.info("[FieldEncryptor] Initialized with key length {} (≥32 OK)", "***");
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isBlank()) return plain;
        if (plain.startsWith(ENC_PREFIX)) return plain;
        return ENC_PREFIX + encryptor.encrypt(plain);
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        if (!encrypted.startsWith(ENC_PREFIX)) return encrypted;
        String payload = encrypted.substring(ENC_PREFIX.length());
        try {
            return encryptor.decrypt(payload);
        } catch (Exception e) {
            // P2-2-2026-08-15 · 容错契约（密钥轮换场景）：解密失败返回密文原样避免数据丢失，
            // 但必须打 warn 日志让运维感知（此前完全静默 = 数据损坏不可追踪）。
            log.warn("[FieldEncryptor] 解密失败，返回密文原样（密钥轮换或数据损坏?）: {}", e.getMessage());
            return encrypted;
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}

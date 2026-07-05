package com.microcourse.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
public class FieldEncryptor {

    private static final String ENC_PREFIX = "ENC:";
    private static final String SALT = "0123456789abcdef";

    private final TextEncryptor encryptor;

    public FieldEncryptor(@Value("${app.security.field-encryption-key:default-32-char-key-for-encrypt}") String password) {
        this.encryptor = Encryptors.delux(password, SALT);
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
            return encrypted;
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENC_PREFIX);
    }
}

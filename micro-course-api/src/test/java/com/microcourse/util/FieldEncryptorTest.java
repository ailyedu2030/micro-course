package com.microcourse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldEncryptor 单元测试。
 *
 * <p>覆盖：
 * <ol>
 *   <li>加密后解密可还原</li>
 *   <li>已加密值不重复加密</li>
 *   <li>非加密值解密返回原值</li>
 *   <li>密钥错误时解密返回原值（容错）</li>
 *   <li>isEncrypted 判定</li>
 *   <li>null/blank 输入安全</li>
 *   <li>构造器校验：密钥过短抛出异常</li>
 *   <li>构造器校验：密钥为空抛出异常</li>
 *   <li>构造器校验：salt 为空抛出异常</li>
 * </ol>
 */
@DisplayName("FieldEncryptor — 字段级加密/解密")
class FieldEncryptorTest {

    private static final String VALID_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String VALID_SALT = "0123456789abcdef";
    private static final String OTHER_KEY = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final String SHORT_KEY = "short-key";
    private static final String PLAIN_TEXT = "13800138000";
    private static final String ANOTHER_TEXT = "user@example.com";

    private FieldEncryptor createEncryptor(String key, String salt) {
        return new FieldEncryptor(key, salt);
    }

    // ─────────────────── 正常加解密 ───────────────────

    @Test
    @DisplayName("加密后解密可还原")
    void encryptThenDecryptReturnsOriginal() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        String encrypted = encryptor.encrypt(PLAIN_TEXT);
        assertTrue(encrypted.startsWith("ENC:"), "加密结果应以 ENC: 开头");
        assertNotEquals(PLAIN_TEXT, encrypted, "加密结果不应等于明文");
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(PLAIN_TEXT, decrypted, "解密后应还原明文");
    }

    @Test
    @DisplayName("不同密钥加密结果不同")
    void differentKeysProduceDifferentCiphertext() {
        FieldEncryptor e1 = createEncryptor(VALID_KEY, VALID_SALT);
        FieldEncryptor e2 = createEncryptor(OTHER_KEY, VALID_SALT);
        String c1 = e1.encrypt(PLAIN_TEXT);
        String c2 = e2.encrypt(PLAIN_TEXT);
        assertNotEquals(c1, c2, "不同密钥加密同一明文应输出不同密文");
    }

    // ─────────────────── 幂等性 ───────────────────

    @Test
    @DisplayName("已加密值不重复加密")
    void alreadyEncryptedValueIsNotReEncrypted() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        String encrypted = encryptor.encrypt(PLAIN_TEXT);
        String doubleEncrypted = encryptor.encrypt(encrypted);
        assertEquals(encrypted, doubleEncrypted, "已加密的值不应再次加密");
    }

    // ─────────────────── 解密容错 ───────────────────

    @Test
    @DisplayName("非加密值解密返回原值")
    void decryptNonEncryptedReturnsOriginal() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        String result = encryptor.decrypt(PLAIN_TEXT);
        assertEquals(PLAIN_TEXT, result, "非 ENC 前缀的值应原样返回");
    }

    @Test
    @DisplayName("密钥错误时解密返回原值（不抛异常）")
    void decryptWithWrongKeyReturnsOriginal() {
        FieldEncryptor e1 = createEncryptor(VALID_KEY, VALID_SALT);
        FieldEncryptor e2 = createEncryptor(OTHER_KEY, VALID_SALT);
        String encrypted = e1.encrypt(PLAIN_TEXT);
        // 用不同密钥解密应返回原值（容错）
        String result = e2.decrypt(encrypted);
        assertEquals(encrypted, result, "密钥不匹配时应返回密文本身而非抛异常");
    }

    // ─────────────────── isEncrypted ───────────────────

    @Test
    @DisplayName("isEncrypted 正确判定加密状态")
    void isEncryptedDetectsEncryptedValues() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        assertFalse(encryptor.isEncrypted(PLAIN_TEXT), "明文不应被判定为已加密");
        assertTrue(encryptor.isEncrypted("ENC:somevalue"), "ENC: 前缀应被判定为已加密");
        assertFalse(encryptor.isEncrypted(null), "null 不应被判定为已加密");
        assertFalse(encryptor.isEncrypted(""), "空字符串不应被判定为已加密");
    }

    // ─────────────────── null/blank 安全 ───────────────────

    @Test
    @DisplayName("null 输入安全：encrypt(null) 返回 null")
    void encryptNullReturnsNull() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        assertNull(encryptor.encrypt(null));
    }

    @Test
    @DisplayName("blank 输入安全：encrypt('') 返回 ''")
    void encryptBlankReturnsBlank() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        assertEquals("", encryptor.encrypt(""));
    }

    @Test
    @DisplayName("null 输入安全：decrypt(null) 返回 null")
    void decryptNullReturnsNull() {
        FieldEncryptor encryptor = createEncryptor(VALID_KEY, VALID_SALT);
        assertNull(encryptor.decrypt(null));
    }

    // ─────────────────── 构造器校验 ───────────────────

    @Test
    @DisplayName("密钥长度 < 32 字符 → 构造器抛出异常")
    void shortKeyThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createEncryptor(SHORT_KEY, VALID_SALT),
                "密钥不足 32 字符时应抛异常");
    }

    @Test
    @DisplayName("空密钥 → 构造器抛出异常")
    void emptyKeyThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createEncryptor("", VALID_SALT),
                "空密钥时应抛异常");
    }

    @Test
    @DisplayName("空 salt → 构造器抛出异常")
    void emptySaltThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createEncryptor(VALID_KEY, ""),
                "空 salt 时应抛异常");
    }

    @Test
    @DisplayName("blank 密钥 → 构造器抛出异常")
    void blankKeyThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createEncryptor("   ", VALID_SALT),
                "空白密钥时应抛异常");
    }
}

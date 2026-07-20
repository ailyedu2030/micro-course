package com.microcourse.plugin.interactive.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioTokenServiceTest {

    private final AudioTokenService service = new AudioTokenService();

    @Test
    void generateToken_returns32HexChars() {
        String token = service.generateToken();
        assertNotNull(token);
        assertEquals(32, token.length());
        assertTrue(service.isValidToken(token));
    }

    @Test
    void generateToken_uniqueness() {
        String t1 = service.generateToken();
        String t2 = service.generateToken();
        assertNotEquals(t1, t2, "tokens should be unique");
    }

    @Test
    void isValidToken_acceptsValidHex() {
        assertTrue(service.isValidToken("0123456789abcdef0123456789abcdef"));
        assertTrue(service.isValidToken("abcdef0123456789abcdef0123456789"));
    }

    @Test
    void isValidToken_rejectsInvalid() {
        assertFalse(service.isValidToken(null));
        assertFalse(service.isValidToken(""));
        assertFalse(service.isValidToken("too-short"));
        assertFalse(service.isValidToken("0123456789ABCDEF0123456789ABCDEF"));  // upper case
        assertFalse(service.isValidToken("0123456789abcdef0123456789abcdez"));  // non-hex
        assertFalse(service.isValidToken("0".repeat(33)));  // 33 chars, too long
    }
}
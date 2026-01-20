package com.chanlun.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AES-256 加密工具类测试
 * 
 * @author Chanlun Team
 */
@DisplayName("EncryptUtil 测试")
class EncryptUtilTest {

    private EncryptUtil encryptUtil;

    @BeforeEach
    void setUp() {
        encryptUtil = new EncryptUtil("test-encryption-key-32-bytes-ok");
    }

    @Test
    @DisplayName("加密和解密应该正确还原原文")
    void encryptAndDecrypt_shouldRestoreOriginalText() {
        String original = "my-secret-api-key-12345";
        
        String encrypted = encryptUtil.encrypt(original);
        String decrypted = encryptUtil.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("相同明文每次加密应产生不同密文")
    void encrypt_samePlaintext_shouldProduceDifferentCiphertext() {
        String original = "same-text";
        
        String encrypted1 = encryptUtil.encrypt(original);
        String encrypted2 = encryptUtil.encrypt(original);
        
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    @DisplayName("空字符串加密应返回空字符串")
    void encrypt_emptyString_shouldReturnEmpty() {
        String result = encryptUtil.encrypt("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("null 加密应返回 null")
    void encrypt_null_shouldReturnNull() {
        String result = encryptUtil.encrypt(null);
        assertNull(result);
    }

    @Test
    @DisplayName("空字符串解密应返回空字符串")
    void decrypt_emptyString_shouldReturnEmpty() {
        String result = encryptUtil.decrypt("");
        assertEquals("", result);
    }

    @Test
    @DisplayName("null 解密应返回 null")
    void decrypt_null_shouldReturnNull() {
        String result = encryptUtil.decrypt(null);
        assertNull(result);
    }

    @Test
    @DisplayName("加密后的密文应为 Base64 格式")
    void encrypt_shouldProduceBase64Output() {
        String original = "test-data";
        String encrypted = encryptUtil.encrypt(original);
        
        // Base64 字符集: A-Z, a-z, 0-9, +, /, =
        assertTrue(encrypted.matches("^[A-Za-z0-9+/=]+$"));
    }

    @Test
    @DisplayName("长文本加密解密应正确")
    void encryptAndDecrypt_longText_shouldWork() {
        String original = "a".repeat(1000);
        
        String encrypted = encryptUtil.encrypt(original);
        String decrypted = encryptUtil.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("特殊字符加密解密应正确")
    void encryptAndDecrypt_specialCharacters_shouldWork() {
        String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?中文日本語한국어";
        
        String encrypted = encryptUtil.encrypt(original);
        String decrypted = encryptUtil.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }
}

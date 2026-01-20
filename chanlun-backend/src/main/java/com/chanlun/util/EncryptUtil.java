package com.chanlun.util;

import com.chanlun.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加密工具类
 * 
 * 用于加密存储敏感数据（API Key、Secret Key、代理密码等）
 * 
 * 加密算法: AES-256-GCM
 * - GCM 模式提供认证加密，同时保证数据的机密性和完整性
 * - 每次加密使用随机 IV，确保相同明文产生不同密文
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
public class EncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;  // GCM 推荐 IV 长度为 12 字节
    private static final int GCM_TAG_LENGTH = 128; // GCM 认证标签长度 128 位

    private final SecretKey secretKey;

    public EncryptUtil(@Value("${app.encryption.key}") String encryptionKey) {
        // 确保密钥长度为 32 字节 (256 位)
        byte[] keyBytes = normalizeKey(encryptionKey);
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密字符串
     * 
     * @param plainText 明文
     * @return Base64 编码的密文 (格式: IV + 密文)
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            // 加密
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 组合 IV 和密文
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new BusinessException(500, "加密失败");
        }
    }

    /**
     * 解密字符串
     * 
     * @param encryptedText Base64 编码的密文
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            // Base64 解码
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // 提取 IV 和密文
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            // 初始化解密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            // 解密
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new BusinessException(500, "解密失败");
        }
    }

    /**
     * 标准化密钥长度为 32 字节
     * 
     * 如果密钥过短，使用 SHA-256 哈希扩展
     * 如果密钥过长，截取前 32 字节
     */
    private byte[] normalizeKey(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] normalizedKey = new byte[32];

        if (keyBytes.length >= 32) {
            System.arraycopy(keyBytes, 0, normalizedKey, 0, 32);
        } else {
            // 密钥过短时，循环填充
            for (int i = 0; i < 32; i++) {
                normalizedKey[i] = keyBytes[i % keyBytes.length];
            }
        }

        return normalizedKey;
    }
}

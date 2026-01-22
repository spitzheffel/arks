package com.chanlun.service;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.exception.BusinessException;
import com.chanlun.mapper.DataSourceMapper;
import com.chanlun.util.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 加密存储与代理配置集成测试 (任务 29.3)
 * 
 * 覆盖：
 * - API Key/Secret Key 加密存储
 * - 代理密码加密存储
 * - 代理配置隔离
 * - 加密解密正确性
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("加密存储与代理配置集成测试")
class EncryptionAndProxyIntegrationTest {

    @Mock
    private DataSourceMapper dataSourceMapper;

    @Mock
    private EncryptUtil encryptUtil;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DataSourceService dataSourceService;

    @BeforeEach
    void setUp() {
        // Mock 加密/解密行为
        when(encryptUtil.encrypt(anyString())).thenAnswer(invocation -> {
            String plain = invocation.getArgument(0);
            return "encrypted_" + plain;
        });
        
        when(encryptUtil.decrypt(anyString())).thenAnswer(invocation -> {
            String encrypted = invocation.getArgument(0);
            if (encrypted.startsWith("encrypted_")) {
                return encrypted.substring(10);
            }
            return encrypted;
        });
    }

    @Nested
    @DisplayName("API密钥加密存储测试")
    class ApiKeyEncryptionTest {

        @Test
        @DisplayName("创建数据源 - API Key 应加密存储")
        void createDataSource_shouldEncryptApiKey() {
            com.chanlun.dto.DataSourceCreateRequest request = new com.chanlun.dto.DataSourceCreateRequest();
            request.setName("Test");
            request.setExchangeType("BINANCE");
            request.setApiKey("plain_api_key");
            request.setSecretKey("plain_secret_key");
            request.setBaseUrl("https://api.binance.com");
            
            when(dataSourceMapper.insert(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.create(request);
            
            ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
            verify(dataSourceMapper).insert(captor.capture());
            
            DataSource saved = captor.getValue();
            assertEquals("encrypted_plain_api_key", saved.getApiKey());
            assertEquals("encrypted_plain_secret_key", saved.getSecretKey());
        }

        @Test
        @DisplayName("创建数据源 - 空 API Key 不加密")
        void createDataSource_nullApiKey_shouldNotEncrypt() {
            com.chanlun.dto.DataSourceCreateRequest request = new com.chanlun.dto.DataSourceCreateRequest();
            request.setName("Test");
            request.setExchangeType("BINANCE");
            request.setApiKey(null);
            request.setSecretKey(null);
            request.setBaseUrl("https://api.binance.com");
            
            when(dataSourceMapper.insert(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.create(request);
            
            ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
            verify(dataSourceMapper).insert(captor.capture());
            
            DataSource saved = captor.getValue();
            assertNull(saved.getApiKey());
            assertNull(saved.getSecretKey());
            verify(encryptUtil, never()).encrypt(anyString());
        }

        @Test
        @DisplayName("更新数据源 - 新 API Key 应加密")
        void updateDataSource_newApiKey_shouldEncrypt() {
            DataSource existing = DataSource.builder()
                    .id(1L)
                    .name("Test")
                    .exchangeType("BINANCE")
                    .apiKey("encrypted_old_key")
                    .secretKey("encrypted_old_secret")
                    .enabled(true)
                    .deleted(false)
                    .build();
            
            com.chanlun.dto.DataSourceUpdateRequest request = new com.chanlun.dto.DataSourceUpdateRequest();
            request.setApiKey("new_api_key");
            request.setSecretKey("new_secret_key");
            
            when(dataSourceMapper.selectById(1L)).thenReturn(existing);
            when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.update(1L, request);
            
            verify(encryptUtil).encrypt("new_api_key");
            verify(encryptUtil).encrypt("new_secret_key");
        }

        @Test
        @DisplayName("获取解密后的 API Key")
        void getDecryptedApiKey_shouldDecrypt() {
            DataSource dataSource = DataSource.builder()
                    .id(1L)
                    .apiKey("encrypted_my_key")
                    .build();
            
            when(dataSourceMapper.selectById(1L)).thenReturn(dataSource);
            
            String decrypted = dataSourceService.getDecryptedApiKey(1L);
            
            assertEquals("my_key", decrypted);
            verify(encryptUtil).decrypt("encrypted_my_key");
        }
    }

    @Nested
    @DisplayName("代理密码加密存储测试")
    class ProxyPasswordEncryptionTest {

        @Test
        @DisplayName("创建数据源 - 代理密码应加密存储")
        void createDataSource_shouldEncryptProxyPassword() {
            com.chanlun.dto.DataSourceCreateRequest request = new com.chanlun.dto.DataSourceCreateRequest();
            request.setName("Test");
            request.setExchangeType("BINANCE");
            request.setBaseUrl("https://api.binance.com");
            request.setProxyEnabled(true);
            request.setProxyType("HTTP");
            request.setProxyHost("127.0.0.1");
            request.setProxyPort(8080);
            request.setProxyUsername("user");
            request.setProxyPassword("plain_proxy_pass");
            
            when(dataSourceMapper.insert(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.create(request);
            
            ArgumentCaptor<DataSource> captor = ArgumentCaptor.forClass(DataSource.class);
            verify(dataSourceMapper).insert(captor.capture());
            
            DataSource saved = captor.getValue();
            assertEquals("encrypted_plain_proxy_pass", saved.getProxyPassword());
        }

        @Test
        @DisplayName("更新数据源 - 新代理密码应加密")
        void updateDataSource_newProxyPassword_shouldEncrypt() {
            DataSource existing = DataSource.builder()
                    .id(1L)
                    .name("Test")
                    .exchangeType("BINANCE")
                    .proxyEnabled(true)
                    .proxyPassword("encrypted_old_pass")
                    .enabled(true)
                    .deleted(false)
                    .build();
            
            com.chanlun.dto.DataSourceUpdateRequest request = new com.chanlun.dto.DataSourceUpdateRequest();
            request.setProxyPassword("new_proxy_pass");
            
            when(dataSourceMapper.selectById(1L)).thenReturn(existing);
            when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.update(1L, request);
            
            verify(encryptUtil).encrypt("new_proxy_pass");
        }
    }

    @Nested
    @DisplayName("代理配置隔离测试")
    class ProxyConfigIsolationTest {

        @Test
        @DisplayName("每个数据源的代理配置独立")
        void proxyConfig_shouldBeIsolated() {
            // 数据源1：启用代理
            DataSource ds1 = DataSource.builder()
                    .id(1L)
                    .name("DS1")
                    .proxyEnabled(true)
                    .proxyType("HTTP")
                    .proxyHost("proxy1.com")
                    .proxyPort(8080)
                    .build();
            
            // 数据源2：不启用代理
            DataSource ds2 = DataSource.builder()
                    .id(2L)
                    .name("DS2")
                    .proxyEnabled(false)
                    .build();
            
            // 验证配置独立
            ProxyConfig config1 = ProxyConfig.fromDataSource(ds1, null);
            ProxyConfig config2 = ProxyConfig.fromDataSource(ds2, null);
            
            assertTrue(config1.isEnabled());
            assertFalse(config2.isEnabled());
            assertEquals("proxy1.com", config1.getHost());
            assertNull(config2.getHost());
        }

        @Test
        @DisplayName("代理配置变更不影响其他数据源")
        void updateProxyConfig_shouldNotAffectOthers() {
            DataSource ds1 = DataSource.builder()
                    .id(1L)
                    .name("DS1")
                    .proxyEnabled(true)
                    .proxyHost("proxy1.com")
                    .enabled(true)
                    .deleted(false)
                    .build();
            
            com.chanlun.dto.DataSourceUpdateRequest request = new com.chanlun.dto.DataSourceUpdateRequest();
            request.setProxyEnabled(false);
            
            when(dataSourceMapper.selectById(1L)).thenReturn(ds1);
            when(dataSourceMapper.updateById(any(DataSource.class))).thenReturn(1);
            
            dataSourceService.update(1L, request);
            
            // 验证只更新了指定数据源
            verify(dataSourceMapper, times(1)).updateById(any(DataSource.class));
            verify(dataSourceMapper, never()).selectById(2L);
        }
    }

    @Nested
    @DisplayName("加密解密正确性测试")
    class EncryptionCorrectnessTest {

        @Test
        @DisplayName("加密后解密应还原原文")
        void encryptThenDecrypt_shouldRestoreOriginal() {
            // 使用真实的 EncryptUtil
            EncryptUtil realEncryptUtil = new EncryptUtil("test-encryption-key-32-bytes-ok");
            
            String original = "my-secret-api-key-12345";
            String encrypted = realEncryptUtil.encrypt(original);
            String decrypted = realEncryptUtil.decrypt(encrypted);
            
            assertEquals(original, decrypted);
            assertNotEquals(original, encrypted);
        }

        @Test
        @DisplayName("相同明文每次加密结果不同（使用随机IV）")
        void encrypt_sameText_shouldProduceDifferentCiphertext() {
            EncryptUtil realEncryptUtil = new EncryptUtil("test-encryption-key-32-bytes-ok");
            
            String original = "my-secret-key";
            String encrypted1 = realEncryptUtil.encrypt(original);
            String encrypted2 = realEncryptUtil.encrypt(original);
            
            assertNotEquals(encrypted1, encrypted2, "相同明文应产生不同密文（随机IV）");
            
            // 但解密后应相同
            assertEquals(original, realEncryptUtil.decrypt(encrypted1));
            assertEquals(original, realEncryptUtil.decrypt(encrypted2));
        }

        @Test
        @DisplayName("空字符串加密解密")
        void encryptDecrypt_emptyString() {
            EncryptUtil realEncryptUtil = new EncryptUtil("test-encryption-key-32-bytes-ok");
            
            String original = "";
            String encrypted = realEncryptUtil.encrypt(original);
            String decrypted = realEncryptUtil.decrypt(encrypted);
            
            assertEquals(original, decrypted);
        }

        @Test
        @DisplayName("特殊字符加密解密")
        void encryptDecrypt_specialCharacters() {
            EncryptUtil realEncryptUtil = new EncryptUtil("test-encryption-key-32-bytes-ok");
            
            String original = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
            String encrypted = realEncryptUtil.encrypt(original);
            String decrypted = realEncryptUtil.decrypt(encrypted);
            
            assertEquals(original, decrypted);
        }
    }
}

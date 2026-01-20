package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.util.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * BinanceClientFactory 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BinanceClientFactoryTest {

    @Mock
    private EncryptUtil encryptUtil;

    private BinanceClientFactory factory;

    @BeforeEach
    void setUp() {
        factory = new BinanceClientFactory(encryptUtil);
    }

    @Test
    @DisplayName("Mock 模式下应创建 BinanceMockClient")
    void createClient_mockEnabled_shouldReturnMockClient() {
        ReflectionTestUtils.setField(factory, "mockEnabled", true);

        DataSource dataSource = DataSource.builder()
                .name("Test")
                .baseUrl("https://api.binance.com")
                .build();

        BinanceClient client = factory.createClient(dataSource);

        assertNotNull(client);
        assertTrue(client instanceof BinanceMockClient);
    }

    @Test
    @DisplayName("非 Mock 模式下应创建真实客户端")
    void createClient_mockDisabled_shouldReturnRealClient() {
        ReflectionTestUtils.setField(factory, "mockEnabled", false);

        when(encryptUtil.decrypt("encryptedApiKey")).thenReturn("apiKey");
        when(encryptUtil.decrypt("encryptedSecretKey")).thenReturn("secretKey");

        DataSource dataSource = DataSource.builder()
                .name("Test")
                .baseUrl("https://api.binance.com")
                .apiKey("encryptedApiKey")
                .secretKey("encryptedSecretKey")
                .proxyEnabled(false)
                .build();

        BinanceClient client = factory.createClient(dataSource);

        assertNotNull(client);
        assertFalse(client instanceof BinanceMockClient);
        assertEquals("apiKey", client.getApiKey());
        assertEquals("secretKey", client.getSecretKey());
    }

    @Test
    @DisplayName("创建带代理的客户端")
    void createClient_withProxy_shouldConfigureProxy() {
        ReflectionTestUtils.setField(factory, "mockEnabled", false);

        when(encryptUtil.decrypt("encryptedApiKey")).thenReturn("apiKey");
        when(encryptUtil.decrypt("encryptedSecretKey")).thenReturn("secretKey");
        when(encryptUtil.decrypt("encryptedProxyPass")).thenReturn("proxyPass");

        DataSource dataSource = DataSource.builder()
                .name("Test")
                .baseUrl("https://api.binance.com")
                .apiKey("encryptedApiKey")
                .secretKey("encryptedSecretKey")
                .proxyEnabled(true)
                .proxyType("HTTP")
                .proxyHost("127.0.0.1")
                .proxyPort(8080)
                .proxyUsername("user")
                .proxyPassword("encryptedProxyPass")
                .build();

        BinanceClient client = factory.createClient(dataSource);

        assertNotNull(client);
    }

    @Test
    @DisplayName("创建测试客户端")
    void createTestClient_shouldReturnClient() {
        ReflectionTestUtils.setField(factory, "mockEnabled", false);

        ProxyConfig proxyConfig = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(8080)
                .build();

        BinanceClient client = factory.createTestClient("https://api.binance.com", proxyConfig);

        assertNotNull(client);
    }

    @Test
    @DisplayName("Mock 模式下创建测试客户端应返回 MockClient")
    void createTestClient_mockEnabled_shouldReturnMockClient() {
        ReflectionTestUtils.setField(factory, "mockEnabled", true);

        BinanceClient client = factory.createTestClient("https://api.binance.com", null);

        assertNotNull(client);
        assertTrue(client instanceof BinanceMockClient);
    }

    @Test
    @DisplayName("创建默认现货客户端")
    void createSpotClient_shouldReturnClient() {
        ReflectionTestUtils.setField(factory, "mockEnabled", false);

        BinanceClient client = factory.createSpotClient();

        assertNotNull(client);
        assertEquals(BinanceClient.DEFAULT_SPOT_BASE_URL, client.getBaseUrl());
    }

    @Test
    @DisplayName("检查 Mock 模式状态")
    void isMockEnabled_shouldReturnCorrectValue() {
        ReflectionTestUtils.setField(factory, "mockEnabled", true);
        assertTrue(factory.isMockEnabled());

        ReflectionTestUtils.setField(factory, "mockEnabled", false);
        assertFalse(factory.isMockEnabled());
    }

    @Test
    @DisplayName("解密失败时应返回 null")
    void createClient_decryptFails_shouldHandleGracefully() {
        ReflectionTestUtils.setField(factory, "mockEnabled", false);

        when(encryptUtil.decrypt(anyString())).thenThrow(new RuntimeException("Decrypt failed"));

        DataSource dataSource = DataSource.builder()
                .name("Test")
                .baseUrl("https://api.binance.com")
                .apiKey("badEncryptedKey")
                .secretKey("badEncryptedSecret")
                .proxyEnabled(false)
                .build();

        BinanceClient client = factory.createClient(dataSource);

        assertNotNull(client);
        assertNull(client.getApiKey());
        assertNull(client.getSecretKey());
    }
}

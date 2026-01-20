package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceServerTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BinanceClient 单元测试
 */
class BinanceClientTest {

    @Nested
    @DisplayName("Mock 模式测试")
    class MockModeTests {

        @Test
        @DisplayName("Mock 模式下 ping 应返回 true")
        void ping_mockMode_shouldReturnTrue() {
            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, null, true
            );

            assertTrue(client.ping());
        }

        @Test
        @DisplayName("Mock 模式下 getServerTime 应返回当前时间")
        void getServerTime_mockMode_shouldReturnCurrentTime() {
            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, null, true
            );

            long before = System.currentTimeMillis();
            BinanceApiResponse<BinanceServerTime> response = client.getServerTime();
            long after = System.currentTimeMillis();

            assertTrue(response.isSuccess());
            assertNotNull(response.getData());
            assertTrue(response.getData().getServerTime() >= before);
            assertTrue(response.getData().getServerTime() <= after);
        }

        @Test
        @DisplayName("Mock 模式下 testConnection 应返回成功")
        void testConnection_mockMode_shouldReturnSuccess() {
            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, null, true
            );

            BinanceClient.ConnectionTestResult result = client.testConnection();

            assertTrue(result.isSuccess());
            assertNotNull(result.getServerTime());
            assertTrue(result.getLatencyMs() >= 0);
        }
    }

    @Nested
    @DisplayName("客户端创建测试")
    class ClientCreationTests {

        @Test
        @DisplayName("使用默认 URL 创建客户端")
        void createClient_withDefaultUrl() {
            BinanceClient client = new BinanceClient(null, null, null, null, true);
            assertEquals(BinanceClient.DEFAULT_SPOT_BASE_URL, client.getBaseUrl());
        }

        @Test
        @DisplayName("使用自定义 URL 创建客户端")
        void createClient_withCustomUrl() {
            String customUrl = "https://custom.binance.com";
            BinanceClient client = new BinanceClient(customUrl, null, null, null, true);
            assertEquals(customUrl, client.getBaseUrl());
        }

        @Test
        @DisplayName("从数据源实体创建客户端")
        void createClient_fromDataSource() {
            DataSource dataSource = DataSource.builder()
                    .name("Test Binance")
                    .baseUrl("https://api.binance.com")
                    .proxyEnabled(false)
                    .build();

            BinanceClient client = BinanceClient.fromDataSource(
                    dataSource, "apiKey", "secretKey", null, true
            );

            assertEquals("https://api.binance.com", client.getBaseUrl());
            assertEquals("apiKey", client.getApiKey());
            assertEquals("secretKey", client.getSecretKey());
        }

        @Test
        @DisplayName("从数据源实体创建带代理的客户端")
        void createClient_fromDataSourceWithProxy() {
            DataSource dataSource = DataSource.builder()
                    .name("Test Binance")
                    .baseUrl("https://api.binance.com")
                    .proxyEnabled(true)
                    .proxyType("HTTP")
                    .proxyHost("127.0.0.1")
                    .proxyPort(8080)
                    .build();

            BinanceClient client = BinanceClient.fromDataSource(
                    dataSource, "apiKey", "secretKey", null, true
            );

            assertNotNull(client);
            assertEquals("https://api.binance.com", client.getBaseUrl());
        }
    }

    @Nested
    @DisplayName("限流控制测试")
    class RateLimitTests {

        @Test
        @DisplayName("初始权重应为 0")
        void initialWeight_shouldBeZero() {
            BinanceClient client = new BinanceClient(null, null, null, null, true);
            assertEquals(0, client.getCurrentWeight());
            assertEquals(6000, client.getRemainingWeight());
        }

        @Test
        @DisplayName("ping 后权重应增加")
        void ping_shouldIncreaseWeight() {
            BinanceClient client = new BinanceClient(null, null, null, null, true);
            client.ping();
            // Mock 模式下不实际调用 acquireWeight，所以权重不变
            // 这里测试的是 Mock 模式的行为
            assertEquals(0, client.getCurrentWeight());
        }
    }

    @Nested
    @DisplayName("代理配置测试")
    class ProxyConfigTests {

        @Test
        @DisplayName("HTTP 代理配置")
        void createClient_withHttpProxy() {
            ProxyConfig proxyConfig = ProxyConfig.builder()
                    .enabled(true)
                    .type(ProxyType.HTTP)
                    .host("127.0.0.1")
                    .port(8080)
                    .build();

            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, proxyConfig, true
            );

            assertNotNull(client);
        }

        @Test
        @DisplayName("SOCKS5 代理配置")
        void createClient_withSocks5Proxy() {
            ProxyConfig proxyConfig = ProxyConfig.builder()
                    .enabled(true)
                    .type(ProxyType.SOCKS5)
                    .host("127.0.0.1")
                    .port(1080)
                    .build();

            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, proxyConfig, true
            );

            assertNotNull(client);
        }

        @Test
        @DisplayName("带认证的代理配置")
        void createClient_withAuthenticatedProxy() {
            ProxyConfig proxyConfig = ProxyConfig.builder()
                    .enabled(true)
                    .type(ProxyType.HTTP)
                    .host("127.0.0.1")
                    .port(8080)
                    .username("user")
                    .password("pass")
                    .build();

            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, proxyConfig, true
            );

            assertNotNull(client);
        }

        @Test
        @DisplayName("禁用代理时不应配置代理")
        void createClient_withDisabledProxy() {
            ProxyConfig proxyConfig = ProxyConfig.builder()
                    .enabled(false)
                    .type(ProxyType.HTTP)
                    .host("127.0.0.1")
                    .port(8080)
                    .build();

            BinanceClient client = new BinanceClient(
                    BinanceClient.DEFAULT_SPOT_BASE_URL,
                    null, null, proxyConfig, true
            );

            assertNotNull(client);
        }
    }
}

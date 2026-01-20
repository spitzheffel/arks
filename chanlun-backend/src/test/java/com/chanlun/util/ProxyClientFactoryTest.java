package com.chanlun.util;

import com.chanlun.config.ProxyConfig;
import com.chanlun.enums.ProxyType;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OkHttp 代理客户端工厂测试
 * 
 * @author Chanlun Team
 */
@DisplayName("ProxyClientFactory 测试")
class ProxyClientFactoryTest {

    private ProxyClientFactory proxyClientFactory;

    @BeforeEach
    void setUp() {
        proxyClientFactory = new ProxyClientFactory();
    }

    @Test
    @DisplayName("创建无代理客户端")
    void createClient_noProxy() {
        OkHttpClient client = proxyClientFactory.createClient();

        assertNotNull(client);
        // OkHttpClient 没有设置代理时 proxy() 返回 null
        assertNull(client.proxy());
    }

    @Test
    @DisplayName("创建 HTTP 代理客户端")
    void createClient_httpProxy() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(8080)
                .build();

        OkHttpClient client = proxyClientFactory.createClient(config);

        assertNotNull(client);
        assertNotNull(client.proxy());
        assertEquals(Proxy.Type.HTTP, client.proxy().type());
    }

    @Test
    @DisplayName("创建 SOCKS5 代理客户端")
    void createClient_socks5Proxy() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.SOCKS5)
                .host("127.0.0.1")
                .port(1080)
                .build();

        OkHttpClient client = proxyClientFactory.createClient(config);

        assertNotNull(client);
        assertNotNull(client.proxy());
        assertEquals(Proxy.Type.SOCKS, client.proxy().type());
    }

    @Test
    @DisplayName("创建带认证的代理客户端")
    void createClient_withAuthentication() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(8080)
                .username("user")
                .password("pass")
                .build();

        OkHttpClient client = proxyClientFactory.createClient(config);

        assertNotNull(client);
        assertNotNull(client.proxyAuthenticator());
    }

    @Test
    @DisplayName("代理配置为空时创建无代理客户端")
    void createClient_nullConfig() {
        OkHttpClient client = proxyClientFactory.createClient(null);

        assertNotNull(client);
        // OkHttpClient 没有设置代理时 proxy() 返回 null
        assertNull(client.proxy());
    }

    @Test
    @DisplayName("代理未启用时创建无代理客户端")
    void createClient_disabledProxy() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(false)
                .build();

        OkHttpClient client = proxyClientFactory.createClient(config);

        assertNotNull(client);
        // OkHttpClient 没有设置代理时 proxy() 返回 null
        assertNull(client.proxy());
    }

    @Test
    @DisplayName("无效代理配置时创建无代理客户端")
    void createClient_invalidConfig() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                // 缺少 host 和 port
                .build();

        OkHttpClient client = proxyClientFactory.createClient(config);

        assertNotNull(client);
        // OkHttpClient 没有设置代理时 proxy() 返回 null
        assertNull(client.proxy());
    }

    @Test
    @DisplayName("创建测试客户端 - 有代理")
    void createTestClient_withProxy() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(8080)
                .build();

        OkHttpClient client = proxyClientFactory.createTestClient(config);

        assertNotNull(client);
        assertNotNull(client.proxy());
        // 测试客户端应该有较短的超时时间
        assertEquals(5000, client.connectTimeoutMillis());
    }

    @Test
    @DisplayName("创建测试客户端 - 无代理")
    void createTestClient_noProxy() {
        OkHttpClient client = proxyClientFactory.createTestClient(null);

        assertNotNull(client);
        // OkHttpClient 没有设置代理时 proxy() 返回 null
        assertNull(client.proxy());
    }
}

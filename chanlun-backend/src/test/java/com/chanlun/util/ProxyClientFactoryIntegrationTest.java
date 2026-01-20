package com.chanlun.util;

import com.chanlun.config.ProxyConfig;
import com.chanlun.enums.ProxyType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代理客户端工厂集成测试
 * 
 * 需要本地代理服务器运行才能通过
 * 默认禁用，手动运行时移除 @Disabled 注解
 * 
 * @author Chanlun Team
 */
@DisplayName("ProxyClientFactory 集成测试")
class ProxyClientFactoryIntegrationTest {

    private ProxyClientFactory proxyClientFactory;

    // 本地代理配置 - 根据实际情况修改
    private static final String PROXY_HOST = "127.0.0.1";
    private static final int PROXY_PORT = 7890;

    @BeforeEach
    void setUp() {
        proxyClientFactory = new ProxyClientFactory();
    }

    @Test
    @DisplayName("HTTP 代理测试 - 访问 httpbin.org")
    @Disabled("需要本地代理服务器")
    void testHttpProxy() throws IOException {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(PROXY_PORT)
                .build();

        OkHttpClient client = proxyClientFactory.createTestClient(config);
        Request request = new Request.Builder()
                .url("https://httpbin.org/ip")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("HTTP 代理测试结果:");
            System.out.println("  状态码: " + response.code());
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("  响应: " + body);
            
            assertTrue(response.isSuccessful(), "HTTP 代理请求应该成功");
            assertTrue(body.contains("origin"), "响应应该包含 origin 字段");
        }
    }

    @Test
    @DisplayName("SOCKS5 代理测试 - 访问 httpbin.org")
    @Disabled("需要本地代理服务器")
    void testSocks5Proxy() throws IOException {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.SOCKS5)
                .host(PROXY_HOST)
                .port(PROXY_PORT)
                .build();

        OkHttpClient client = proxyClientFactory.createTestClient(config);
        Request request = new Request.Builder()
                .url("https://httpbin.org/ip")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("SOCKS5 代理测试结果:");
            System.out.println("  状态码: " + response.code());
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("  响应: " + body);
            
            assertTrue(response.isSuccessful(), "SOCKS5 代理请求应该成功");
            assertTrue(body.contains("origin"), "响应应该包含 origin 字段");
        }
    }

    @Test
    @DisplayName("HTTP 代理测试 - 访问币安 API")
    @Disabled("需要本地代理服务器")
    void testHttpProxyBinance() throws IOException {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host(PROXY_HOST)
                .port(PROXY_PORT)
                .build();

        OkHttpClient client = proxyClientFactory.createTestClient(config);
        Request request = new Request.Builder()
                .url("https://api.binance.com/api/v3/ping")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            System.out.println("币安 API 测试结果 (HTTP 代理):");
            System.out.println("  状态码: " + response.code());
            String body = response.body() != null ? response.body().string() : "";
            System.out.println("  响应: " + body);
            
            assertTrue(response.isSuccessful(), "币安 API 请求应该成功");
        }
    }
}

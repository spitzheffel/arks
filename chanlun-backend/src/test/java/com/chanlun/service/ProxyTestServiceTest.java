package com.chanlun.service;

import com.chanlun.config.ProxyConfig;
import com.chanlun.dto.ProxyTestResult;
import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import com.chanlun.exception.BusinessException;
import com.chanlun.util.EncryptUtil;
import com.chanlun.util.ProxyClientFactory;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 代理测试服务测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProxyTestService 测试")
class ProxyTestServiceTest {

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private ProxyClientFactory proxyClientFactory;

    @Mock
    private EncryptUtil encryptUtil;

    @Mock
    private OkHttpClient mockClient;

    @Mock
    private Call mockCall;

    @InjectMocks
    private ProxyTestService proxyTestService;

    private DataSource testDataSource;

    @BeforeEach
    void setUp() {
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .proxyEnabled(true)
                .proxyType("HTTP")
                .proxyHost("127.0.0.1")
                .proxyPort(8080)
                .proxyUsername("user")
                .proxyPassword("encrypted-password")
                .build();
    }

    @Test
    @DisplayName("测试代理 - 代理未启用应抛出异常")
    void testProxy_proxyNotEnabled_shouldThrowException() {
        testDataSource.setProxyEnabled(false);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        assertThrows(BusinessException.class, () -> proxyTestService.testProxy(1L));
    }

    @Test
    @DisplayName("测试代理配置 - 配置为空应抛出异常")
    void testProxyConfig_nullConfig_shouldThrowException() {
        assertThrows(BusinessException.class, () -> proxyTestService.testProxyConfig(null));
    }

    @Test
    @DisplayName("测试代理配置 - 代理未启用应抛出异常")
    void testProxyConfig_disabledProxy_shouldThrowException() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(false)
                .build();

        assertThrows(BusinessException.class, () -> proxyTestService.testProxyConfig(config));
    }

    @Test
    @DisplayName("测试代理配置 - 无效配置应抛出异常")
    void testProxyConfig_invalidConfig_shouldThrowException() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                // 缺少 host 和 port
                .build();

        assertThrows(BusinessException.class, () -> proxyTestService.testProxyConfig(config));
    }

    @Test
    @DisplayName("测试代理 - 连接成功")
    void testProxy_success() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);

        // 模拟成功响应
        Response mockResponse = new Response.Builder()
                .request(new Request.Builder().url("https://httpbin.org/ip").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create("{\"origin\": \"1.2.3.4\"}", MediaType.parse("application/json")))
                .build();
        when(mockCall.execute()).thenReturn(mockResponse);

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertTrue(result.isSuccess());
        assertEquals("代理连接成功", result.getMessage());
        assertNotNull(result.getLatencyMs());
        assertEquals(200, result.getStatusCode());
    }

    @Test
    @DisplayName("测试代理 - HTTP 错误")
    void testProxy_httpError() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);

        // 模拟 403 错误响应
        Response mockResponse = new Response.Builder()
                .request(new Request.Builder().url("https://httpbin.org/ip").build())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body(ResponseBody.create("", MediaType.parse("text/plain")))
                .build();
        when(mockCall.execute()).thenReturn(mockResponse);

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("403"));
    }

    @Test
    @DisplayName("测试代理 - 连接超时")
    void testProxy_connectionTimeout() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Connection timed out"));

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("超时"));
    }

    @Test
    @DisplayName("测试代理 - 连接被拒绝")
    void testProxy_connectionRefused() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Connection refused"));

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("被拒绝"));
    }

    @Test
    @DisplayName("测试代理 - 无法解析主机")
    void testProxy_unknownHost() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Unable to resolve host"));

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("无法解析"));
    }

    @Test
    @DisplayName("测试代理 - 认证失败")
    void testProxy_authenticationFailed() throws IOException {
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(encryptUtil.decrypt("encrypted-password")).thenReturn("password");
        when(proxyClientFactory.createTestClient(any(ProxyConfig.class))).thenReturn(mockClient);
        when(mockClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenThrow(new IOException("Proxy Authentication Required 407"));

        ProxyTestResult result = proxyTestService.testProxy(1L);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("认证失败"));
    }
}

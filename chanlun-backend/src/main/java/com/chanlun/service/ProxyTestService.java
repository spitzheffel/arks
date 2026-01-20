package com.chanlun.service;

import com.chanlun.config.ProxyConfig;
import com.chanlun.dto.ProxyTestResult;
import com.chanlun.entity.DataSource;
import com.chanlun.exception.BusinessException;
import com.chanlun.util.EncryptUtil;
import com.chanlun.util.ProxyClientFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * 代理测试服务
 * 
 * 提供代理连接测试功能
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyTestService {

    private final DataSourceService dataSourceService;
    private final ProxyClientFactory proxyClientFactory;
    private final EncryptUtil encryptUtil;

    /**
     * 测试 URL - 使用 httpbin.org 的 IP 接口
     * 该接口返回请求者的 IP 地址，可用于验证代理是否生效
     */
    private static final String TEST_URL = "https://httpbin.org/ip";

    /**
     * 备用测试 URL - 使用 Google 的连通性检测
     */
    private static final String FALLBACK_TEST_URL = "https://www.google.com/generate_204";

    /**
     * 测试数据源的代理连接
     * 
     * @param dataSourceId 数据源 ID
     * @return 测试结果
     */
    public ProxyTestResult testProxy(Long dataSourceId) {
        DataSource dataSource = dataSourceService.findById(dataSourceId);
        
        if (!Boolean.TRUE.equals(dataSource.getProxyEnabled())) {
            throw new BusinessException("该数据源未启用代理");
        }

        // 获取解密后的代理密码
        String decryptedPassword = null;
        if (dataSource.getProxyPassword() != null) {
            decryptedPassword = encryptUtil.decrypt(dataSource.getProxyPassword());
        }

        // 创建代理配置
        ProxyConfig proxyConfig = ProxyConfig.fromDataSource(dataSource, decryptedPassword);
        
        if (!proxyConfig.isValid()) {
            throw new BusinessException("代理配置无效，请检查代理地址和端口");
        }

        return testProxyConnection(proxyConfig);
    }

    /**
     * 测试代理配置（不保存到数据库）
     * 
     * @param proxyConfig 代理配置
     * @return 测试结果
     */
    public ProxyTestResult testProxyConfig(ProxyConfig proxyConfig) {
        if (proxyConfig == null || !proxyConfig.isEnabled()) {
            throw new BusinessException("代理配置为空或未启用");
        }
        
        if (!proxyConfig.isValid()) {
            throw new BusinessException("代理配置无效，请检查代理地址和端口");
        }

        return testProxyConnection(proxyConfig);
    }

    /**
     * 执行代理连接测试
     * 
     * @param proxyConfig 代理配置
     * @return 测试结果
     */
    private ProxyTestResult testProxyConnection(ProxyConfig proxyConfig) {
        Instant startTime = Instant.now();
        
        OkHttpClient client = proxyClientFactory.createTestClient(proxyConfig);
        
        // 首先尝试主测试 URL
        ProxyTestResult result = executeTest(client, TEST_URL, startTime);
        
        if (!result.isSuccess()) {
            // 如果主 URL 失败，尝试备用 URL
            log.debug("Primary test URL failed, trying fallback URL");
            result = executeTest(client, FALLBACK_TEST_URL, startTime);
        }

        return result;
    }

    /**
     * 执行单次测试请求
     * 
     * @param client OkHttpClient
     * @param url 测试 URL
     * @param startTime 开始时间
     * @return 测试结果
     */
    private ProxyTestResult executeTest(OkHttpClient client, String url, Instant startTime) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            
            if (response.isSuccessful() || response.code() == 204) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.info("Proxy test successful: url={}, status={}, latency={}ms", 
                        url, response.code(), latencyMs);
                
                return ProxyTestResult.builder()
                        .success(true)
                        .message("代理连接成功")
                        .latencyMs(latencyMs)
                        .statusCode(response.code())
                        .responseBody(responseBody.length() > 500 ? 
                                responseBody.substring(0, 500) + "..." : responseBody)
                        .testUrl(url)
                        .build();
            } else {
                log.warn("Proxy test failed: url={}, status={}", url, response.code());
                return ProxyTestResult.builder()
                        .success(false)
                        .message("代理连接失败: HTTP " + response.code())
                        .latencyMs(latencyMs)
                        .statusCode(response.code())
                        .testUrl(url)
                        .build();
            }
        } catch (IOException e) {
            long latencyMs = Duration.between(startTime, Instant.now()).toMillis();
            log.error("Proxy test error: url={}, error={}", url, e.getMessage());
            
            String errorMessage = parseErrorMessage(e);
            return ProxyTestResult.builder()
                    .success(false)
                    .message(errorMessage)
                    .latencyMs(latencyMs)
                    .errorDetail(e.getMessage())
                    .testUrl(url)
                    .build();
        }
    }

    /**
     * 解析错误信息，返回用户友好的提示
     * 
     * @param e 异常
     * @return 错误信息
     */
    private String parseErrorMessage(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return "代理连接失败: 未知错误";
        }
        
        if (message.contains("Connection refused")) {
            return "代理连接被拒绝，请检查代理地址和端口是否正确";
        }
        if (message.contains("Connection timed out") || message.contains("connect timed out")) {
            return "代理连接超时，请检查代理服务器是否可用";
        }
        if (message.contains("Unable to resolve host") || message.contains("UnknownHostException")) {
            return "无法解析代理主机地址，请检查代理地址是否正确";
        }
        if (message.contains("Proxy Authentication Required") || message.contains("407")) {
            return "代理认证失败，请检查用户名和密码";
        }
        if (message.contains("SOCKS")) {
            return "SOCKS 代理连接失败，请检查代理类型和配置";
        }
        
        return "代理连接失败: " + message;
    }
}

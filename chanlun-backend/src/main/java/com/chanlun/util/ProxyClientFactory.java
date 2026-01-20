package com.chanlun.util;

import com.chanlun.config.ProxyConfig;
import com.chanlun.enums.ProxyType;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

/**
 * OkHttp 代理客户端工厂
 * 
 * 根据代理配置创建 OkHttpClient 实例
 * 支持 HTTP 和 SOCKS5 代理类型
 * 支持代理认证（用户名/密码）
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
public class ProxyClientFactory {

    /**
     * 默认连接超时时间（秒）
     */
    private static final int DEFAULT_CONNECT_TIMEOUT = 10;

    /**
     * 默认读取超时时间（秒）
     */
    private static final int DEFAULT_READ_TIMEOUT = 30;

    /**
     * 默认写入超时时间（秒）
     */
    private static final int DEFAULT_WRITE_TIMEOUT = 30;

    /**
     * 创建 OkHttpClient（不使用代理）
     * 
     * @return OkHttpClient 实例
     */
    public OkHttpClient createClient() {
        return createClientBuilder().build();
    }

    /**
     * 根据代理配置创建 OkHttpClient
     * 
     * @param proxyConfig 代理配置
     * @return OkHttpClient 实例
     */
    public OkHttpClient createClient(ProxyConfig proxyConfig) {
        if (proxyConfig == null || !proxyConfig.isEnabled()) {
            log.debug("Creating OkHttpClient without proxy");
            return createClient();
        }

        if (!proxyConfig.isValid()) {
            log.warn("Invalid proxy configuration, creating client without proxy");
            return createClient();
        }

        OkHttpClient.Builder builder = createClientBuilder();
        configureProxy(builder, proxyConfig);

        log.debug("Created OkHttpClient with {} proxy: {}:{}", 
                proxyConfig.getType(), proxyConfig.getHost(), proxyConfig.getPort());
        
        return builder.build();
    }

    /**
     * 创建基础的 OkHttpClient.Builder
     * 
     * @return OkHttpClient.Builder
     */
    private OkHttpClient.Builder createClientBuilder() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT))
                .readTimeout(Duration.ofSeconds(DEFAULT_READ_TIMEOUT))
                .writeTimeout(Duration.ofSeconds(DEFAULT_WRITE_TIMEOUT))
                .retryOnConnectionFailure(true);
    }

    /**
     * 配置代理
     * 
     * @param builder OkHttpClient.Builder
     * @param proxyConfig 代理配置
     */
    private void configureProxy(OkHttpClient.Builder builder, ProxyConfig proxyConfig) {
        // 创建代理
        Proxy proxy = createProxy(proxyConfig);
        builder.proxy(proxy);

        // 配置代理认证
        if (proxyConfig.requiresAuthentication()) {
            Authenticator proxyAuthenticator = createProxyAuthenticator(
                    proxyConfig.getUsername(), 
                    proxyConfig.getPassword()
            );
            builder.proxyAuthenticator(proxyAuthenticator);
            log.debug("Configured proxy authentication for user: {}", proxyConfig.getUsername());
        }
    }

    /**
     * 创建 Java Proxy 对象
     * 
     * @param proxyConfig 代理配置
     * @return Proxy 对象
     */
    private Proxy createProxy(ProxyConfig proxyConfig) {
        Proxy.Type javaProxyType = convertProxyType(proxyConfig.getType());
        InetSocketAddress address = new InetSocketAddress(
                proxyConfig.getHost(), 
                proxyConfig.getPort()
        );
        return new Proxy(javaProxyType, address);
    }

    /**
     * 转换代理类型
     * 
     * @param proxyType 应用代理类型
     * @return Java Proxy.Type
     */
    private Proxy.Type convertProxyType(ProxyType proxyType) {
        if (proxyType == null) {
            return Proxy.Type.HTTP;
        }
        return switch (proxyType) {
            case HTTP -> Proxy.Type.HTTP;
            case SOCKS5 -> Proxy.Type.SOCKS;
        };
    }

    /**
     * 创建代理认证器
     * 
     * @param username 用户名
     * @param password 密码
     * @return Authenticator
     */
    private Authenticator createProxyAuthenticator(String username, String password) {
        return (route, response) -> {
            if (response.request().header("Proxy-Authorization") != null) {
                // 已经尝试过认证，避免无限循环
                return null;
            }
            String credential = Credentials.basic(username, password != null ? password : "");
            return response.request().newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
    }

    /**
     * 创建用于测试代理连接的 OkHttpClient
     * 使用较短的超时时间
     * 
     * @param proxyConfig 代理配置
     * @return OkHttpClient 实例
     */
    public OkHttpClient createTestClient(ProxyConfig proxyConfig) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .writeTimeout(Duration.ofSeconds(10))
                .retryOnConnectionFailure(false);

        if (proxyConfig != null && proxyConfig.isEnabled() && proxyConfig.isValid()) {
            configureProxy(builder, proxyConfig);
        }

        return builder.build();
    }
}

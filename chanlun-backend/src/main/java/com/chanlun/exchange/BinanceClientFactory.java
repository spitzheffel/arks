package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 币安客户端工厂
 * 
 * 根据配置创建 BinanceClient 实例
 * 支持 Mock 模式切换
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceClientFactory {

    private final EncryptUtil encryptUtil;

    @Value("${app.exchange.api-mock:false}")
    private boolean mockEnabled;

    /**
     * 创建币安客户端
     * 
     * @param dataSource 数据源实体
     * @return BinanceClient 实例
     */
    public BinanceClient createClient(DataSource dataSource) {
        if (mockEnabled) {
            log.info("Creating mock Binance client for data source: {}", dataSource.getName());
            return new BinanceMockClient(dataSource.getBaseUrl());
        }

        // 解密敏感信息
        String decryptedApiKey = decryptIfNotEmpty(dataSource.getApiKey());
        String decryptedSecretKey = decryptIfNotEmpty(dataSource.getSecretKey());
        String decryptedProxyPassword = decryptIfNotEmpty(dataSource.getProxyPassword());

        // 创建代理配置
        ProxyConfig proxyConfig = ProxyConfig.fromDataSource(dataSource, decryptedProxyPassword);

        log.info("Creating Binance client for data source: {}, proxy enabled: {}", 
                dataSource.getName(), proxyConfig.isEnabled());

        return new BinanceClient(
                dataSource.getBaseUrl(),
                decryptedApiKey,
                decryptedSecretKey,
                proxyConfig,
                false
        );
    }

    /**
     * 创建用于连接测试的客户端（不需要 API Key）
     * 
     * @param baseUrl API 基础 URL
     * @param proxyConfig 代理配置
     * @return BinanceClient 实例
     */
    public BinanceClient createTestClient(String baseUrl, ProxyConfig proxyConfig) {
        if (mockEnabled) {
            log.info("Creating mock Binance test client");
            return new BinanceMockClient(baseUrl);
        }

        return new BinanceClient(baseUrl, null, null, proxyConfig, false);
    }

    /**
     * 创建默认现货客户端（用于公开接口）
     * 
     * @return BinanceClient 实例
     */
    public BinanceClient createSpotClient() {
        if (mockEnabled) {
            return new BinanceMockClient(BinanceClient.DEFAULT_SPOT_BASE_URL);
        }
        return new BinanceClient(BinanceClient.DEFAULT_SPOT_BASE_URL, null, null, null, false);
    }

    /**
     * 检查是否启用 Mock 模式
     */
    public boolean isMockEnabled() {
        return mockEnabled;
    }

    /**
     * 解密字符串（如果非空）
     */
    private String decryptIfNotEmpty(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return null;
        }
        try {
            return encryptUtil.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Failed to decrypt value: {}", e.getMessage());
            return null;
        }
    }
}

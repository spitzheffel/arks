package com.chanlun.config;

import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代理配置测试
 * 
 * @author Chanlun Team
 */
@DisplayName("ProxyConfig 测试")
class ProxyConfigTest {

    @Test
    @DisplayName("从数据源创建代理配置 - 代理启用")
    void fromDataSource_proxyEnabled() {
        DataSource dataSource = DataSource.builder()
                .proxyEnabled(true)
                .proxyType("HTTP")
                .proxyHost("127.0.0.1")
                .proxyPort(8080)
                .proxyUsername("user")
                .build();

        ProxyConfig config = ProxyConfig.fromDataSource(dataSource, "password");

        assertTrue(config.isEnabled());
        assertEquals(ProxyType.HTTP, config.getType());
        assertEquals("127.0.0.1", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals("user", config.getUsername());
        assertEquals("password", config.getPassword());
    }

    @Test
    @DisplayName("从数据源创建代理配置 - 代理未启用")
    void fromDataSource_proxyDisabled() {
        DataSource dataSource = DataSource.builder()
                .proxyEnabled(false)
                .build();

        ProxyConfig config = ProxyConfig.fromDataSource(dataSource, null);

        assertFalse(config.isEnabled());
    }

    @Test
    @DisplayName("从数据源创建代理配置 - 数据源为空")
    void fromDataSource_nullDataSource() {
        ProxyConfig config = ProxyConfig.fromDataSource(null, null);

        assertFalse(config.isEnabled());
    }

    @Test
    @DisplayName("从数据源创建代理配置 - SOCKS5 代理")
    void fromDataSource_socks5Proxy() {
        DataSource dataSource = DataSource.builder()
                .proxyEnabled(true)
                .proxyType("SOCKS5")
                .proxyHost("socks.example.com")
                .proxyPort(1080)
                .build();

        ProxyConfig config = ProxyConfig.fromDataSource(dataSource, null);

        assertTrue(config.isEnabled());
        assertEquals(ProxyType.SOCKS5, config.getType());
    }

    @Test
    @DisplayName("验证配置有效性 - 有效配置")
    void isValid_validConfig() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(8080)
                .build();

        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("验证配置有效性 - 未启用代理")
    void isValid_disabledProxy() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(false)
                .build();

        assertTrue(config.isValid());
    }

    @Test
    @DisplayName("验证配置有效性 - 缺少主机")
    void isValid_missingHost() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .port(8080)
                .build();

        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("验证配置有效性 - 无效端口")
    void isValid_invalidPort() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(0)
                .build();

        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("验证配置有效性 - 端口超出范围")
    void isValid_portOutOfRange() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .type(ProxyType.HTTP)
                .host("127.0.0.1")
                .port(70000)
                .build();

        assertFalse(config.isValid());
    }

    @Test
    @DisplayName("检查是否需要认证 - 有用户名")
    void requiresAuthentication_withUsername() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .username("user")
                .build();

        assertTrue(config.requiresAuthentication());
    }

    @Test
    @DisplayName("检查是否需要认证 - 无用户名")
    void requiresAuthentication_withoutUsername() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(true)
                .build();

        assertFalse(config.requiresAuthentication());
    }

    @Test
    @DisplayName("检查是否需要认证 - 代理未启用")
    void requiresAuthentication_proxyDisabled() {
        ProxyConfig config = ProxyConfig.builder()
                .enabled(false)
                .username("user")
                .build();

        assertFalse(config.requiresAuthentication());
    }
}

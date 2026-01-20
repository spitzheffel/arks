package com.chanlun.config;

import com.chanlun.entity.DataSource;
import com.chanlun.enums.ProxyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理配置类
 * 
 * 封装代理连接所需的所有配置信息
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyConfig {

    /**
     * 是否启用代理
     */
    private boolean enabled;

    /**
     * 代理类型 (HTTP/SOCKS5)
     */
    private ProxyType type;

    /**
     * 代理主机地址
     */
    private String host;

    /**
     * 代理端口
     */
    private Integer port;

    /**
     * 代理用户名（可选）
     */
    private String username;

    /**
     * 代理密码（可选，已解密）
     */
    private String password;

    /**
     * 从数据源实体创建代理配置
     * 
     * @param dataSource 数据源实体
     * @param decryptedPassword 解密后的代理密码
     * @return 代理配置
     */
    public static ProxyConfig fromDataSource(DataSource dataSource, String decryptedPassword) {
        if (dataSource == null || !Boolean.TRUE.equals(dataSource.getProxyEnabled())) {
            return ProxyConfig.builder()
                    .enabled(false)
                    .build();
        }

        ProxyType proxyType = null;
        if (dataSource.getProxyType() != null) {
            try {
                proxyType = ProxyType.fromCode(dataSource.getProxyType());
            } catch (IllegalArgumentException e) {
                // 无效的代理类型，使用默认 HTTP
                proxyType = ProxyType.HTTP;
            }
        }

        return ProxyConfig.builder()
                .enabled(true)
                .type(proxyType)
                .host(dataSource.getProxyHost())
                .port(dataSource.getProxyPort())
                .username(dataSource.getProxyUsername())
                .password(decryptedPassword)
                .build();
    }

    /**
     * 检查代理配置是否有效
     * 
     * @return true 如果配置有效
     */
    public boolean isValid() {
        if (!enabled) {
            return true; // 未启用代理，配置有效
        }
        return type != null && host != null && !host.isBlank() && port != null && port > 0 && port <= 65535;
    }

    /**
     * 检查是否需要认证
     * 
     * @return true 如果需要用户名密码认证
     */
    public boolean requiresAuthentication() {
        return enabled && username != null && !username.isBlank();
    }
}

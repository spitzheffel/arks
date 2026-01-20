package com.chanlun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据源 DTO
 * 
 * 用于 API 响应，不包含敏感信息（API Key、Secret Key 等）
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataSourceDTO {

    private Long id;
    private String name;
    private String exchangeType;
    private String baseUrl;
    private String wsUrl;
    private Boolean proxyEnabled;
    private String proxyType;
    private String proxyHost;
    private Integer proxyPort;
    private String proxyUsername;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * 是否已配置 API Key（不返回实际值）
     */
    private Boolean hasApiKey;

    /**
     * 是否已配置 Secret Key（不返回实际值）
     */
    private Boolean hasSecretKey;

    /**
     * 是否已配置代理密码（不返回实际值）
     */
    private Boolean hasProxyPassword;
}

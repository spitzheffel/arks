package com.chanlun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建数据源请求 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSourceCreateRequest {

    @NotBlank(message = "数据源名称不能为空")
    @Size(max = 50, message = "数据源名称长度不能超过50个字符")
    private String name;

    @NotBlank(message = "交易所类型不能为空")
    @Size(max = 20, message = "交易所类型长度不能超过20个字符")
    private String exchangeType;

    @Size(max = 255, message = "API Key长度不能超过255个字符")
    private String apiKey;

    @Size(max = 500, message = "Secret Key长度不能超过500个字符")
    private String secretKey;

    @Size(max = 255, message = "API基础URL长度不能超过255个字符")
    private String baseUrl;

    @Size(max = 255, message = "WebSocket URL长度不能超过255个字符")
    private String wsUrl;

    private Boolean proxyEnabled;

    @Size(max = 10, message = "代理类型长度不能超过10个字符")
    private String proxyType;

    @Size(max = 100, message = "代理地址长度不能超过100个字符")
    private String proxyHost;

    private Integer proxyPort;

    @Size(max = 100, message = "代理用户名长度不能超过100个字符")
    private String proxyUsername;

    @Size(max = 255, message = "代理密码长度不能超过255个字符")
    private String proxyPassword;
}

package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理测试结果 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyTestResult {

    /**
     * 测试是否成功
     */
    private boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 响应延迟（毫秒）
     */
    private Long latencyMs;

    /**
     * HTTP 状态码
     */
    private Integer statusCode;

    /**
     * 响应内容（截取）
     */
    private String responseBody;

    /**
     * 测试使用的 URL
     */
    private String testUrl;

    /**
     * 错误详情（仅失败时）
     */
    private String errorDetail;
}

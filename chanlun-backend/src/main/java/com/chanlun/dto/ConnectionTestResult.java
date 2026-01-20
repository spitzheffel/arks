package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 连接测试结果 DTO
 * 
 * 用于返回数据源连接测试的结果
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {
    
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
     * 服务器时间（UTC）
     */
    private Instant serverTime;
    
    /**
     * 本地时间与服务器时间差（毫秒）
     */
    private Long timeDiffMs;
}

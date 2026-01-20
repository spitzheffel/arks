package com.chanlun.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 币安 API 响应封装
 * 
 * @param <T> 数据类型
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceApiResponse<T> {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误码（成功时为 0）
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 创建成功响应
     */
    public static <T> BinanceApiResponse<T> success(T data) {
        return BinanceApiResponse.<T>builder()
                .success(true)
                .code(0)
                .message("success")
                .data(data)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static <T> BinanceApiResponse<T> error(int code, String message) {
        return BinanceApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .build();
    }
}

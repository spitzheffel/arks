package com.chanlun.exception;

import lombok.Getter;

/**
 * 业务异常类
 * 
 * 用于表示业务逻辑错误，如参数校验失败、资源不存在等
 * 
 * @author Chanlun Team
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    /**
     * 构造函数（默认 400 错误码）
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 构造函数（自定义错误码）
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造函数（带原因）
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

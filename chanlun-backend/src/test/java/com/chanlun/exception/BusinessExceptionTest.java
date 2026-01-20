package com.chanlun.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessException 单元测试
 */
@DisplayName("BusinessException 测试")
class BusinessExceptionTest {

    @Test
    @DisplayName("默认构造函数应使用 400 错误码")
    void defaultConstructorShouldUse400Code() {
        BusinessException ex = new BusinessException("测试错误");
        
        assertEquals(400, ex.getCode());
        assertEquals("测试错误", ex.getMessage());
    }

    @Test
    @DisplayName("自定义错误码构造函数应正确设置")
    void customCodeConstructorShouldWork() {
        BusinessException ex = new BusinessException(403, "禁止访问");
        
        assertEquals(403, ex.getCode());
        assertEquals("禁止访问", ex.getMessage());
    }

    @Test
    @DisplayName("带原因的构造函数应正确设置")
    void constructorWithCauseShouldWork() {
        Throwable cause = new RuntimeException("原始错误");
        BusinessException ex = new BusinessException(500, "服务错误", cause);
        
        assertEquals(500, ex.getCode());
        assertEquals("服务错误", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}

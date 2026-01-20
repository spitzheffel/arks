package com.chanlun.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceNotFoundException 单元测试
 */
@DisplayName("ResourceNotFoundException 测试")
class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("简单消息构造函数应使用 404 错误码")
    void simpleMessageConstructorShouldUse404Code() {
        ResourceNotFoundException ex = new ResourceNotFoundException("资源不存在");
        
        assertEquals(404, ex.getCode());
        assertEquals("资源不存在", ex.getMessage());
    }

    @Test
    @DisplayName("带资源类型和 ID 的构造函数应正确格式化消息")
    void constructorWithTypeAndIdShouldFormatMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("DataSource", 123L);
        
        assertEquals(404, ex.getCode());
        assertEquals("DataSource not found with id: 123", ex.getMessage());
    }

    @Test
    @DisplayName("带资源类型和标识符的构造函数应正确格式化消息")
    void constructorWithTypeAndIdentifierShouldFormatMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Symbol", "BTCUSDT");
        
        assertEquals(404, ex.getCode());
        assertEquals("Symbol not found: BTCUSDT", ex.getMessage());
    }
}

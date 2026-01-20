package com.chanlun.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试
 */
@DisplayName("ApiResponse 测试")
class ApiResponseTest {

    @Test
    @DisplayName("success() 应返回 code=200 和 message=success")
    void testSuccessWithoutData() {
        ApiResponse<Void> response = ApiResponse.success();
        
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("success(data) 应返回带数据的成功响应")
    void testSuccessWithData() {
        String data = "test data";
        ApiResponse<String> response = ApiResponse.success(data);
        
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals(data, response.getData());
    }

    @Test
    @DisplayName("success(message, data) 应返回自定义消息和数据")
    void testSuccessWithMessageAndData() {
        String message = "操作成功";
        String data = "test data";
        ApiResponse<String> response = ApiResponse.success(message, data);
        
        assertEquals(200, response.getCode());
        assertEquals(message, response.getMessage());
        assertEquals(data, response.getData());
    }

    @Test
    @DisplayName("error(code, message) 应返回错误响应")
    void testError() {
        ApiResponse<Void> response = ApiResponse.error(500, "服务器错误");
        
        assertEquals(500, response.getCode());
        assertEquals("服务器错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("badRequest() 应返回 400 错误")
    void testBadRequest() {
        ApiResponse<Void> response = ApiResponse.badRequest("参数错误");
        
        assertEquals(400, response.getCode());
        assertEquals("参数错误", response.getMessage());
    }

    @Test
    @DisplayName("notFound() 应返回 404 错误")
    void testNotFound() {
        ApiResponse<Void> response = ApiResponse.notFound("资源不存在");
        
        assertEquals(404, response.getCode());
        assertEquals("资源不存在", response.getMessage());
    }

    @Test
    @DisplayName("serverError() 应返回 500 错误")
    void testServerError() {
        ApiResponse<Void> response = ApiResponse.serverError("内部错误");
        
        assertEquals(500, response.getCode());
        assertEquals("内部错误", response.getMessage());
    }
}

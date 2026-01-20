package com.chanlun.exception;

import com.chanlun.dto.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GlobalExceptionHandler 单元测试
 */
@DisplayName("GlobalExceptionHandler 测试")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("处理 BusinessException 应返回对应状态码")
    void testHandleBusinessException() {
        BusinessException ex = new BusinessException(400, "业务错误");
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("业务错误", response.getBody().getMessage());
    }

    @Test
    @DisplayName("处理 ResourceNotFoundException 应返回 404")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("DataSource", 1L);
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleResourceNotFoundException(ex);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("DataSource"));
    }


    @Test
    @DisplayName("处理 MethodArgumentNotValidException 应返回 400 和字段错误")
    void testHandleValidationException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "name", "不能为空");
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
        
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("参数校验失败"));
        assertNotNull(response.getBody().getData());
        assertEquals("不能为空", response.getBody().getData().get("name"));
    }

    @Test
    @DisplayName("处理 ConstraintViolationException 应返回 400")
    void testHandleConstraintViolationException() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        
        when(path.toString()).thenReturn("id");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("必须大于0");
        violations.add(violation);
        
        ConstraintViolationException ex = new ConstraintViolationException(violations);
        
        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleConstraintViolationException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("参数校验失败"));
    }

    @Test
    @DisplayName("处理未知异常应返回 500")
    void testHandleException() {
        Exception ex = new RuntimeException("未知错误");
        
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(ex);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("服务器内部错误", response.getBody().getMessage());
    }
}

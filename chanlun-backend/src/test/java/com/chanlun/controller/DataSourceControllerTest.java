package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.*;
import com.chanlun.entity.DataSource;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.service.DataSourceService;
import com.chanlun.service.MarketService;
import com.chanlun.service.ProxyTestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 数据源控制器测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(DataSourceController.class)
@DisplayName("DataSourceController 测试")
class DataSourceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DataSourceService dataSourceService;

    @MockBean
    private ProxyTestService proxyTestService;

    @MockBean
    private BinanceClientFactory binanceClientFactory;

    @MockBean
    private MarketService marketService;

    private DataSourceDTO createTestDTO() {
        return DataSourceDTO.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .wsUrl("wss://stream.binance.com:9443/ws")
                .proxyEnabled(false)
                .enabled(true)
                .hasApiKey(true)
                .hasSecretKey(true)
                .hasProxyPassword(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/datasources - 获取列表成功")
    void list_success() throws Exception {
        Page<DataSourceDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(dataSourceService.list(anyInt(), anyInt(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/datasources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("Test Binance"));
    }

    @Test
    @DisplayName("GET /api/v1/datasources/{id} - 获取详情成功")
    void getById_success() throws Exception {
        when(dataSourceService.getById(1L)).thenReturn(createTestDTO());

        mockMvc.perform(get("/api/v1/datasources/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("Test Binance"));
    }

    @Test
    @DisplayName("GET /api/v1/datasources/{id} - 不存在返回404")
    void getById_notFound() throws Exception {
        when(dataSourceService.getById(999L)).thenThrow(new ResourceNotFoundException("数据源不存在: 999"));

        mockMvc.perform(get("/api/v1/datasources/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("POST /api/v1/datasources - 创建成功")
    void create_success() throws Exception {
        DataSourceCreateRequest request = DataSourceCreateRequest.builder()
                .name("New Binance")
                .exchangeType("BINANCE")
                .apiKey("test-api-key")
                .secretKey("test-secret-key")
                .build();

        when(dataSourceService.create(any(DataSourceCreateRequest.class))).thenReturn(createTestDTO());

        mockMvc.perform(post("/api/v1/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据源创建成功"));
    }

    @Test
    @DisplayName("POST /api/v1/datasources - 缺少必填字段返回400")
    void create_missingRequiredField() throws Exception {
        DataSourceCreateRequest request = DataSourceCreateRequest.builder()
                .exchangeType("BINANCE")
                .build();

        mockMvc.perform(post("/api/v1/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/datasources/{id} - 更新成功")
    void update_success() throws Exception {
        DataSourceUpdateRequest request = DataSourceUpdateRequest.builder()
                .name("Updated Name")
                .build();

        when(dataSourceService.update(eq(1L), any(DataSourceUpdateRequest.class))).thenReturn(createTestDTO());

        mockMvc.perform(put("/api/v1/datasources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据源更新成功"));
    }

    @Test
    @DisplayName("DELETE /api/v1/datasources/{id} - 删除成功")
    void delete_success() throws Exception {
        doNothing().when(dataSourceService).delete(1L);

        mockMvc.perform(delete("/api/v1/datasources/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据源删除成功"));
    }

    @Test
    @DisplayName("PATCH /api/v1/datasources/{id}/status - 启用成功")
    void updateStatus_enable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        when(dataSourceService.updateStatus(1L, true)).thenReturn(createTestDTO());

        mockMvc.perform(patch("/api/v1/datasources/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据源已启用"));
    }

    @Test
    @DisplayName("PATCH /api/v1/datasources/{id}/status - 禁用成功")
    void updateStatus_disable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(false)
                .build();

        DataSourceDTO disabledDTO = createTestDTO();
        disabledDTO.setEnabled(false);
        when(dataSourceService.updateStatus(1L, false)).thenReturn(disabledDTO);

        mockMvc.perform(patch("/api/v1/datasources/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("数据源已禁用"));
    }

    @Test
    @DisplayName("POST /api/v1/datasources/{id}/test - 测试连接成功")
    void testConnection() throws Exception {
        DataSource dataSourceEntity = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .build();
        
        BinanceClient mockClient = mock(BinanceClient.class);
        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(true)
                .message("Connection successful")
                .latencyMs(100L)
                .serverTime(Instant.now())
                .timeDiffMs(50L)
                .build();
        
        when(dataSourceService.findById(1L)).thenReturn(dataSourceEntity);
        when(binanceClientFactory.createClient(any(DataSource.class))).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(testResult);

        mockMvc.perform(post("/api/v1/datasources/1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("连接测试成功"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.latencyMs").value(100));
        
        verify(mockClient).close();
    }
    
    @Test
    @DisplayName("POST /api/v1/datasources/{id}/test - 测试连接失败")
    void testConnection_failed() throws Exception {
        DataSource dataSourceEntity = DataSource.builder()
                .id(1L)
                .name("Test Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .build();
        
        BinanceClient mockClient = mock(BinanceClient.class);
        BinanceClient.ConnectionTestResult testResult = BinanceClient.ConnectionTestResult.builder()
                .success(false)
                .message("Ping failed")
                .latencyMs(5000L)
                .build();
        
        when(dataSourceService.findById(1L)).thenReturn(dataSourceEntity);
        when(binanceClientFactory.createClient(any(DataSource.class))).thenReturn(mockClient);
        when(mockClient.testConnection()).thenReturn(testResult);

        mockMvc.perform(post("/api/v1/datasources/1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Ping failed"))
                .andExpect(jsonPath("$.data.success").value(false));
        
        verify(mockClient).close();
    }
}

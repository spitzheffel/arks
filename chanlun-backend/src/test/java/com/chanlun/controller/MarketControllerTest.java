package com.chanlun.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.ApiResponse;
import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.StatusUpdateRequest;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.service.MarketService;
import com.chanlun.service.SymbolService;
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
 * 市场控制器测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(MarketController.class)
@DisplayName("MarketController 测试")
class MarketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MarketService marketService;

    @MockBean
    private SymbolService symbolService;

    private MarketDTO createTestDTO() {
        return MarketDTO.builder()
                .id(1L)
                .dataSourceId(1L)
                .dataSourceName("Test Binance")
                .name("现货市场")
                .marketType("SPOT")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/markets - 获取列表成功")
    void list_success() throws Exception {
        Page<MarketDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(marketService.list(anyInt(), anyInt(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("现货市场"))
                .andExpect(jsonPath("$.data.records[0].marketType").value("SPOT"));
    }

    @Test
    @DisplayName("GET /api/v1/markets - 按数据源ID筛选")
    void list_filterByDataSourceId() throws Exception {
        Page<MarketDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(marketService.list(eq(1), eq(20), eq(1L), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/v1/markets")
                        .param("dataSourceId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].dataSourceId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/markets - 限制每页最大100条")
    void list_limitPageSize() throws Exception {
        Page<MarketDTO> page = new Page<>(1, 100);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(marketService.list(eq(1), eq(100), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/markets")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(marketService).list(eq(1), eq(100), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/markets/all - 获取所有市场")
    void listAll_success() throws Exception {
        when(marketService.listAll(any(), any(), any())).thenReturn(List.of(createTestDTO()));

        mockMvc.perform(get("/api/v1/markets/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("现货市场"));
    }

    @Test
    @DisplayName("GET /api/v1/markets/{id} - 获取详情成功")
    void getById_success() throws Exception {
        when(marketService.getById(1L)).thenReturn(createTestDTO());

        mockMvc.perform(get("/api/v1/markets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("现货市场"))
                .andExpect(jsonPath("$.data.marketType").value("SPOT"));
    }

    @Test
    @DisplayName("GET /api/v1/markets/{id} - 不存在返回404")
    void getById_notFound() throws Exception {
        when(marketService.getById(999L)).thenThrow(new ResourceNotFoundException("市场不存在: 999"));

        mockMvc.perform(get("/api/v1/markets/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("PATCH /api/v1/markets/{id}/status - 启用成功")
    void updateStatus_enable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        when(marketService.updateStatus(1L, true)).thenReturn(createTestDTO());

        mockMvc.perform(patch("/api/v1/markets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("市场已启用"));
    }

    @Test
    @DisplayName("PATCH /api/v1/markets/{id}/status - 禁用成功")
    void updateStatus_disable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(false)
                .build();

        MarketDTO disabledDTO = createTestDTO();
        disabledDTO.setEnabled(false);
        when(marketService.updateStatus(1L, false)).thenReturn(disabledDTO);

        mockMvc.perform(patch("/api/v1/markets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("市场已禁用"));
    }

    @Test
    @DisplayName("PATCH /api/v1/markets/{id}/status - 缺少enabled字段返回400")
    void updateStatus_missingEnabled() throws Exception {
        mockMvc.perform(patch("/api/v1/markets/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PATCH /api/v1/markets/{id}/status - 市场不存在返回404")
    void updateStatus_notFound() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        when(marketService.updateStatus(999L, true))
                .thenThrow(new ResourceNotFoundException("市场不存在: 999"));

        mockMvc.perform(patch("/api/v1/markets/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}

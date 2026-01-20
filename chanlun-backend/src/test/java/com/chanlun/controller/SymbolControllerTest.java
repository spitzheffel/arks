package com.chanlun.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.StatusUpdateRequest;
import com.chanlun.dto.SymbolDTO;
import com.chanlun.dto.SymbolIntervalsRequest;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 交易对控制器测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(SymbolController.class)
@DisplayName("SymbolController 测试")
class SymbolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SymbolService symbolService;

    private SymbolDTO createTestDTO() {
        return SymbolDTO.builder()
                .id(1L)
                .marketId(1L)
                .marketName("现货市场")
                .marketType("SPOT")
                .dataSourceId(1L)
                .dataSourceName("Test Binance")
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .pricePrecision(8)
                .quantityPrecision(8)
                .realtimeSyncEnabled(false)
                .historySyncEnabled(false)
                .syncIntervals(List.of("1m", "1h"))
                .status("TRADING")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }


    @Test
    @DisplayName("GET /api/v1/symbols - 获取列表成功")
    void list_success() throws Exception {
        Page<SymbolDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(symbolService.list(anyInt(), anyInt(), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/symbols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.data.records[0].baseAsset").value("BTC"));
    }

    @Test
    @DisplayName("GET /api/v1/symbols - 按市场ID筛选")
    void list_filterByMarketId() throws Exception {
        Page<SymbolDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(symbolService.list(eq(1), eq(20), eq(1L), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/v1/symbols")
                        .param("marketId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].marketId").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/symbols - 按关键词搜索")
    void list_filterByKeyword() throws Exception {
        Page<SymbolDTO> page = new Page<>(1, 20);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(symbolService.list(eq(1), eq(20), isNull(), isNull(), eq("BTC"), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/v1/symbols")
                        .param("keyword", "BTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].symbol").value("BTCUSDT"));
    }

    @Test
    @DisplayName("GET /api/v1/symbols - 限制每页最大100条")
    void list_limitPageSize() throws Exception {
        Page<SymbolDTO> page = new Page<>(1, 100);
        page.setRecords(List.of(createTestDTO()));
        page.setTotal(1);

        when(symbolService.list(eq(1), eq(100), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/symbols")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(symbolService).list(eq(1), eq(100), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/symbols/all - 获取所有交易对")
    void listAll_success() throws Exception {
        when(symbolService.listAll(any(), any(), any(), any(), any())).thenReturn(List.of(createTestDTO()));

        mockMvc.perform(get("/api/v1/symbols/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].symbol").value("BTCUSDT"));
    }

    @Test
    @DisplayName("GET /api/v1/symbols/{id} - 获取详情成功")
    void getById_success() throws Exception {
        when(symbolService.getById(1L)).thenReturn(createTestDTO());

        mockMvc.perform(get("/api/v1/symbols/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.data.baseAsset").value("BTC"));
    }

    @Test
    @DisplayName("GET /api/v1/symbols/{id} - 不存在返回404")
    void getById_notFound() throws Exception {
        when(symbolService.getById(999L)).thenThrow(new ResourceNotFoundException("交易对不存在: 999"));

        mockMvc.perform(get("/api/v1/symbols/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }


    @Test
    @DisplayName("PATCH /api/v1/symbols/{id}/realtime-sync - 开启实时同步成功")
    void updateRealtimeSync_enable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        SymbolDTO enabledDTO = createTestDTO();
        enabledDTO.setRealtimeSyncEnabled(true);
        when(symbolService.updateRealtimeSyncStatus(1L, true)).thenReturn(enabledDTO);

        mockMvc.perform(patch("/api/v1/symbols/1/realtime-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("实时同步已开启"))
                .andExpect(jsonPath("$.data.realtimeSyncEnabled").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/symbols/{id}/realtime-sync - 关闭实时同步成功")
    void updateRealtimeSync_disable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(false)
                .build();

        when(symbolService.updateRealtimeSyncStatus(1L, false)).thenReturn(createTestDTO());

        mockMvc.perform(patch("/api/v1/symbols/1/realtime-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("实时同步已关闭"));
    }

    @Test
    @DisplayName("PATCH /api/v1/symbols/{id}/realtime-sync - 市场未启用返回400")
    void updateRealtimeSync_marketDisabled() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        when(symbolService.updateRealtimeSyncStatus(1L, true))
                .thenThrow(new BusinessException("市场未启用，无法开启同步"));

        mockMvc.perform(patch("/api/v1/symbols/1/realtime-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PATCH /api/v1/symbols/{id}/history-sync - 开启历史同步成功")
    void updateHistorySync_enable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(true)
                .build();

        SymbolDTO enabledDTO = createTestDTO();
        enabledDTO.setHistorySyncEnabled(true);
        when(symbolService.updateHistorySyncStatus(1L, true)).thenReturn(enabledDTO);

        mockMvc.perform(patch("/api/v1/symbols/1/history-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("历史同步已开启"))
                .andExpect(jsonPath("$.data.historySyncEnabled").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/symbols/{id}/history-sync - 关闭历史同步成功")
    void updateHistorySync_disable_success() throws Exception {
        StatusUpdateRequest request = StatusUpdateRequest.builder()
                .enabled(false)
                .build();

        when(symbolService.updateHistorySyncStatus(1L, false)).thenReturn(createTestDTO());

        mockMvc.perform(patch("/api/v1/symbols/1/history-sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("历史同步已关闭"));
    }


    @Test
    @DisplayName("PUT /api/v1/symbols/{id}/intervals - 配置同步周期成功")
    void updateIntervals_success() throws Exception {
        SymbolIntervalsRequest request = SymbolIntervalsRequest.builder()
                .intervals(List.of("1m", "5m", "1h"))
                .build();

        SymbolDTO updatedDTO = createTestDTO();
        updatedDTO.setSyncIntervals(List.of("1m", "5m", "1h"));
        when(symbolService.updateSyncIntervals(1L, List.of("1m", "5m", "1h"))).thenReturn(updatedDTO);

        mockMvc.perform(put("/api/v1/symbols/1/intervals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("同步周期配置成功"));
    }

    @Test
    @DisplayName("PUT /api/v1/symbols/{id}/intervals - 不支持的周期返回400")
    void updateIntervals_invalidInterval() throws Exception {
        SymbolIntervalsRequest request = SymbolIntervalsRequest.builder()
                .intervals(List.of("1s", "1m"))
                .build();

        when(symbolService.updateSyncIntervals(1L, List.of("1s", "1m")))
                .thenThrow(new BusinessException("不支持的同步周期: 1s"));

        mockMvc.perform(put("/api/v1/symbols/1/intervals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/symbols/{id}/intervals - 缺少intervals字段返回400")
    void updateIntervals_missingIntervals() throws Exception {
        mockMvc.perform(put("/api/v1/symbols/1/intervals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/symbols/intervals - 获取支持的周期列表")
    void getValidIntervals_success() throws Exception {
        Set<String> intervals = Set.of("1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w", "1M");
        when(symbolService.getValidIntervals()).thenReturn(intervals);

        mockMvc.perform(get("/api/v1/symbols/intervals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }
}

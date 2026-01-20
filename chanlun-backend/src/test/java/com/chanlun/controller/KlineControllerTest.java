package com.chanlun.controller;

import com.chanlun.entity.Kline;
import com.chanlun.exception.BusinessException;
import com.chanlun.service.KlineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * K线数据控制器测试
 * 
 * @author Chanlun Team
 */
@WebMvcTest(KlineController.class)
@DisplayName("KlineController 测试")
class KlineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KlineService klineService;

    private Kline createTestKline(Long id, Instant openTime) {
        return Kline.builder()
                .id(id)
                .symbolId(1L)
                .interval("1h")
                .openTime(openTime)
                .open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00"))
                .low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00"))
                .volume(new BigDecimal("1000.00"))
                .quoteVolume(new BigDecimal("50000000.00"))
                .trades(5000)
                .closeTime(openTime.plusSeconds(3600))
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/klines - 带时间范围查询成功")
    void getKlines_withTimeRange_success() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        List<Kline> klines = List.of(
                createTestKline(1L, Instant.parse("2025-01-01T00:00:00Z")),
                createTestKline(2L, Instant.parse("2025-01-01T01:00:00Z"))
        );

        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(500)))
                .thenReturn(klines);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h")
                        .param("startTime", "2025-01-01T00:00:00Z")
                        .param("endTime", "2025-01-01T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].symbolId").value(1))
                .andExpect(jsonPath("$.data[0].interval").value("1h"))
                .andExpect(jsonPath("$.data[0].open").value(50000.00));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 带 limit 参数查询成功")
    void getKlines_withLimit_success() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        List<Kline> klines = List.of(
                createTestKline(1L, Instant.parse("2025-01-01T00:00:00Z"))
        );

        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(100)))
                .thenReturn(klines);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h")
                        .param("startTime", "2025-01-01T00:00:00Z")
                        .param("endTime", "2025-01-01T12:00:00Z")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/klines - limit 超过最大值时限制为1000")
    void getKlines_limitExceedsMax_cappedTo1000() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h")
                        .param("startTime", "2025-01-01T00:00:00Z")
                        .param("endTime", "2025-01-01T12:00:00Z")
                        .param("limit", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 验证 limit 被限制为 1000
        verify(klineService).getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 无时间范围时返回最新数据")
    void getKlines_noTimeRange_returnsLatest() throws Exception {
        Instant minTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant maxTime = Instant.parse("2025-01-01T12:00:00Z");
        
        List<Kline> klines = List.of(
                createTestKline(1L, Instant.parse("2025-01-01T00:00:00Z"))
        );

        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(maxTime);
        when(klineService.getMinOpenTime(1L, "1h")).thenReturn(minTime);
        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(minTime), eq(maxTime), eq(500)))
                .thenReturn(klines);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 无数据时返回空列表")
    void getKlines_noData_returnsEmptyList() throws Exception {
        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(null);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 缺少 symbolId 返回400")
    void getKlines_missingSymbolId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/klines")
                        .param("interval", "1h"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/klines - 缺少 interval 返回400")
    void getKlines_missingInterval_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/klines - 无效的 interval 返回400")
    void getKlines_invalidInterval_returns400() throws Exception {
        when(klineService.getMaxOpenTime(1L, "1s"))
                .thenThrow(new BusinessException("不支持的时间周期: 1s"));

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1s"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 只有 startTime 时查询到现在")
    void getKlines_onlyStartTime_queriesToNow() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        
        List<Kline> klines = List.of(
                createTestKline(1L, startTime)
        );

        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(startTime), any(Instant.class), eq(500)))
                .thenReturn(klines);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h")
                        .param("startTime", "2025-01-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/klines - 只有 endTime 时从最早查询")
    void getKlines_onlyEndTime_queriesFromEarliest() throws Exception {
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant minTime = Instant.parse("2025-01-01T00:00:00Z");
        
        List<Kline> klines = List.of(
                createTestKline(1L, minTime)
        );

        when(klineService.getMinOpenTime(1L, "1h")).thenReturn(minTime);
        when(klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                eq(1L), eq("1h"), eq(minTime), eq(endTime), eq(500)))
                .thenReturn(klines);

        mockMvc.perform(get("/api/v1/klines")
                        .param("symbolId", "1")
                        .param("interval", "1h")
                        .param("endTime", "2025-01-01T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ==================== 删除接口测试 ====================

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 删除成功")
    void deleteKlines_success() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        when(klineService.deleteByTimeRange(eq(1L), eq("1h"), eq(startTime), eq(endTime)))
                .thenReturn(100);

        String requestBody = """
                {
                    "interval": "1h",
                    "startTime": "2025-01-01T00:00:00Z",
                    "endTime": "2025-01-01T12:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.symbolId").value(1))
                .andExpect(jsonPath("$.data.interval").value("1h"))
                .andExpect(jsonPath("$.data.deletedCount").value(100));
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 无数据删除返回0")
    void deleteKlines_noData_returnsZero() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        when(klineService.deleteByTimeRange(eq(1L), eq("1h"), eq(startTime), eq(endTime)))
                .thenReturn(0);

        String requestBody = """
                {
                    "interval": "1h",
                    "startTime": "2025-01-01T00:00:00Z",
                    "endTime": "2025-01-01T12:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.deletedCount").value(0));
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 缺少 interval 返回400")
    void deleteKlines_missingInterval_returns400() throws Exception {
        String requestBody = """
                {
                    "startTime": "2025-01-01T00:00:00Z",
                    "endTime": "2025-01-01T12:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 缺少 startTime 返回400")
    void deleteKlines_missingStartTime_returns400() throws Exception {
        String requestBody = """
                {
                    "interval": "1h",
                    "endTime": "2025-01-01T12:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 缺少 endTime 返回400")
    void deleteKlines_missingEndTime_returns400() throws Exception {
        String requestBody = """
                {
                    "interval": "1h",
                    "startTime": "2025-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - 无效的 interval 返回400")
    void deleteKlines_invalidInterval_returns400() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T12:00:00Z");
        
        when(klineService.deleteByTimeRange(eq(1L), eq("1s"), eq(startTime), eq(endTime)))
                .thenThrow(new BusinessException("不支持的时间周期: 1s"));

        String requestBody = """
                {
                    "interval": "1s",
                    "startTime": "2025-01-01T00:00:00Z",
                    "endTime": "2025-01-01T12:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("DELETE /api/v1/klines/symbols/{symbolId} - startTime 晚于 endTime 返回400")
    void deleteKlines_startTimeAfterEndTime_returns400() throws Exception {
        Instant startTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant endTime = Instant.parse("2025-01-01T00:00:00Z");
        
        when(klineService.deleteByTimeRange(eq(1L), eq("1h"), eq(startTime), eq(endTime)))
                .thenThrow(new BusinessException("开始时间不能晚于结束时间"));

        String requestBody = """
                {
                    "interval": "1h",
                    "startTime": "2025-01-01T12:00:00Z",
                    "endTime": "2025-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(delete("/api/v1/klines/symbols/1")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}

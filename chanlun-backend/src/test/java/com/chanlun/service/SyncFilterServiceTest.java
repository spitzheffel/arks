package com.chanlun.service;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 同步对象筛选服务测试
 */
@ExtendWith(MockitoExtension.class)
class SyncFilterServiceTest {

    @Mock
    private SymbolService symbolService;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private SyncFilterService syncFilterService;

    private Symbol testSymbol;
    private SymbolDTO testSymbolDTO;

    @BeforeEach
    void setUp() {
        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(true)
                .syncIntervals("1m,5m,1h")
                .build();

        testSymbolDTO = SymbolDTO.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(true)
                .syncIntervals(Arrays.asList("1m", "5m", "1h"))
                .build();
    }

    @Test
    @DisplayName("获取实时同步目标 - 全局开关关闭时返回空列表")
    void getRealtimeSyncTargets_WhenGlobalDisabled_ReturnsEmpty() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

        List<Symbol> result = syncFilterService.getRealtimeSyncTargets();

        assertTrue(result.isEmpty());
        verify(symbolService, never()).getRealtimeSyncEnabledSymbols();
    }

    @Test
    @DisplayName("获取实时同步目标 - 全局开关开启时返回符合条件的交易对")
    void getRealtimeSyncTargets_WhenGlobalEnabled_ReturnsFilteredSymbols() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.getRealtimeSyncEnabledSymbols()).thenReturn(List.of(testSymbol));

        List<Symbol> result = syncFilterService.getRealtimeSyncTargets();

        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    @DisplayName("获取实时同步目标 - 过滤掉没有配置同步周期的交易对")
    void getRealtimeSyncTargets_FiltersSymbolsWithoutIntervals() {
        Symbol symbolWithoutIntervals = Symbol.builder()
                .id(2L)
                .symbol("ETHUSDT")
                .realtimeSyncEnabled(true)
                .syncIntervals(null)
                .build();

        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        when(symbolService.getRealtimeSyncEnabledSymbols())
                .thenReturn(Arrays.asList(testSymbol, symbolWithoutIntervals));

        List<Symbol> result = syncFilterService.getRealtimeSyncTargets();

        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    @DisplayName("获取历史同步目标 - 自动同步关闭时返回空列表")
    void getHistorySyncTargets_WhenAutoSyncDisabled_ReturnsEmpty() {
        when(systemConfigService.isHistoryAutoSyncEnabled()).thenReturn(false);

        List<SymbolDTO> result = syncFilterService.getHistorySyncTargets();

        assertTrue(result.isEmpty());
        verify(symbolService, never()).getHistorySyncEnabled();
    }

    @Test
    @DisplayName("获取历史同步目标 - 自动同步开启时返回符合条件的交易对")
    void getHistorySyncTargets_WhenAutoSyncEnabled_ReturnsFilteredSymbols() {
        when(systemConfigService.isHistoryAutoSyncEnabled()).thenReturn(true);
        when(symbolService.getHistorySyncEnabled()).thenReturn(List.of(testSymbolDTO));

        List<SymbolDTO> result = syncFilterService.getHistorySyncTargets();

        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    @DisplayName("获取缺口检测目标 - 返回启用历史同步的交易对")
    void getGapDetectTargets_ReturnsHistorySyncEnabledSymbols() {
        when(symbolService.getHistorySyncEnabled()).thenReturn(List.of(testSymbolDTO));

        List<SymbolDTO> result = syncFilterService.getGapDetectTargets();

        assertEquals(1, result.size());
        assertEquals("BTCUSDT", result.get(0).getSymbol());
    }

    @Test
    @DisplayName("获取自动缺口回补目标 - 全局开关关闭时返回空列表")
    void getAutoGapFillTargets_WhenGlobalDisabled_ReturnsEmpty() {
        when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);

        List<SymbolDTO> result = syncFilterService.getAutoGapFillTargets();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("获取自动缺口回补目标 - 全局开关开启时返回符合条件的交易对")
    void getAutoGapFillTargets_WhenGlobalEnabled_ReturnsFilteredSymbols() {
        when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
        when(symbolService.getHistorySyncEnabled()).thenReturn(List.of(testSymbolDTO));

        List<SymbolDTO> result = syncFilterService.getAutoGapFillTargets();

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("获取有效同步周期 - Symbol 实体")
    void getValidSyncIntervals_FromSymbol() {
        List<String> result = syncFilterService.getValidSyncIntervals(testSymbol);

        assertEquals(3, result.size());
        assertTrue(result.contains("1m"));
        assertTrue(result.contains("5m"));
        assertTrue(result.contains("1h"));
    }

    @Test
    @DisplayName("获取有效同步周期 - SymbolDTO")
    void getValidSyncIntervals_FromSymbolDTO() {
        List<String> result = syncFilterService.getValidSyncIntervals(testSymbolDTO);

        assertEquals(3, result.size());
        assertTrue(result.contains("1m"));
        assertTrue(result.contains("5m"));
        assertTrue(result.contains("1h"));
    }

    @Test
    @DisplayName("获取有效同步周期 - 过滤无效周期")
    void getValidSyncIntervals_FiltersInvalidIntervals() {
        Symbol symbolWithInvalidIntervals = Symbol.builder()
                .syncIntervals("1m,1s,invalid,1h")
                .build();

        List<String> result = syncFilterService.getValidSyncIntervals(symbolWithInvalidIntervals);

        assertEquals(2, result.size());
        assertTrue(result.contains("1m"));
        assertTrue(result.contains("1h"));
        assertFalse(result.contains("1s"));
        assertFalse(result.contains("invalid"));
    }

    @Test
    @DisplayName("获取有效同步周期 - 空配置返回空列表")
    void getValidSyncIntervals_EmptyConfig_ReturnsEmpty() {
        Symbol symbolWithEmptyIntervals = Symbol.builder()
                .syncIntervals(null)
                .build();

        List<String> result = syncFilterService.getValidSyncIntervals(symbolWithEmptyIntervals);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("检查交易对是否符合实时同步条件")
    void isEligibleForRealtimeSync() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);

        assertTrue(syncFilterService.isEligibleForRealtimeSync(testSymbol));
    }

    @Test
    @DisplayName("检查交易对是否符合实时同步条件 - 全局开关关闭")
    void isEligibleForRealtimeSync_GlobalDisabled() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(false);

        assertFalse(syncFilterService.isEligibleForRealtimeSync(testSymbol));
    }

    @Test
    @DisplayName("检查交易对是否符合实时同步条件 - 交易对未启用")
    void isEligibleForRealtimeSync_SymbolDisabled() {
        when(systemConfigService.isRealtimeSyncEnabled()).thenReturn(true);
        testSymbol.setRealtimeSyncEnabled(false);

        assertFalse(syncFilterService.isEligibleForRealtimeSync(testSymbol));
    }

    @Test
    @DisplayName("检查交易对是否符合历史同步条件")
    void isEligibleForHistorySync() {
        assertTrue(syncFilterService.isEligibleForHistorySync(testSymbol));
    }

    @Test
    @DisplayName("检查交易对是否符合历史同步条件 - 交易对未启用")
    void isEligibleForHistorySync_SymbolDisabled() {
        testSymbol.setHistorySyncEnabled(false);

        assertFalse(syncFilterService.isEligibleForHistorySync(testSymbol));
    }

    @Test
    @DisplayName("检查交易对是否符合历史同步条件 - 无同步周期配置")
    void isEligibleForHistorySync_NoIntervals() {
        testSymbol.setSyncIntervals(null);

        assertFalse(syncFilterService.isEligibleForHistorySync(testSymbol));
    }
}

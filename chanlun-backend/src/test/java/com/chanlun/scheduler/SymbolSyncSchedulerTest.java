package com.chanlun.scheduler;

import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.SymbolSyncResult;
import com.chanlun.entity.DataSource;
import com.chanlun.service.DataSourceService;
import com.chanlun.service.MarketService;
import com.chanlun.service.SymbolService;
import com.chanlun.service.SystemConfigService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 交易对同步定时任务测试
 */
@ExtendWith(MockitoExtension.class)
class SymbolSyncSchedulerTest {

    @Mock
    private SymbolService symbolService;

    @Mock
    private MarketService marketService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private SymbolSyncScheduler symbolSyncScheduler;

    private MarketDTO testMarket;
    private DataSource testDataSource;
    private SymbolSyncResult successResult;

    @BeforeEach
    void setUp() {
        testMarket = MarketDTO.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货市场")
                .marketType("SPOT")
                .enabled(true)
                .build();

        testDataSource = DataSource.builder()
                .id(1L)
                .name("Binance")
                .enabled(true)
                .deleted(false)
                .build();

        successResult = SymbolSyncResult.builder()
                .success(true)
                .message("同步成功")
                .syncedCount(100)
                .createdCount(10)
                .updatedCount(5)
                .existingCount(85)
                .build();
    }

    @Test
    @DisplayName("同步所有交易对 - 成功场景")
    void syncAllSymbols_Success() {
        when(marketService.listAll(null, null, true)).thenReturn(List.of(testMarket));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolService.syncSymbolsFromBinance(1L)).thenReturn(successResult);

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(1, summary.marketsProcessed);
        assertEquals(100, summary.totalSymbols);
        assertEquals(10, summary.createdCount);
        assertEquals(5, summary.updatedCount);
        assertEquals(0, summary.failureCount);
    }

    @Test
    @DisplayName("同步所有交易对 - 无启用的市场")
    void syncAllSymbols_NoEnabledMarkets() {
        when(marketService.listAll(null, null, true)).thenReturn(Collections.emptyList());

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(0, summary.marketsProcessed);
        assertEquals(0, summary.totalSymbols);
        verify(symbolService, never()).syncSymbolsFromBinance(any());
    }

    @Test
    @DisplayName("同步所有交易对 - 数据源已禁用")
    void syncAllSymbols_DataSourceDisabled() {
        testDataSource.setEnabled(false);
        when(marketService.listAll(null, null, true)).thenReturn(List.of(testMarket));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(0, summary.marketsProcessed);
        verify(symbolService, never()).syncSymbolsFromBinance(any());
    }

    @Test
    @DisplayName("同步所有交易对 - 数据源已删除")
    void syncAllSymbols_DataSourceDeleted() {
        testDataSource.setDeleted(true);
        when(marketService.listAll(null, null, true)).thenReturn(List.of(testMarket));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(0, summary.marketsProcessed);
        verify(symbolService, never()).syncSymbolsFromBinance(any());
    }

    @Test
    @DisplayName("同步所有交易对 - 部分失败")
    void syncAllSymbols_PartialFailure() {
        MarketDTO market2 = MarketDTO.builder()
                .id(2L)
                .dataSourceId(1L)
                .name("合约市场")
                .marketType("USDT_M")
                .enabled(true)
                .build();

        SymbolSyncResult failResult = SymbolSyncResult.builder()
                .success(false)
                .message("同步失败")
                .build();

        when(marketService.listAll(null, null, true)).thenReturn(Arrays.asList(testMarket, market2));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolService.syncSymbolsFromBinance(1L)).thenReturn(successResult);
        when(symbolService.syncSymbolsFromBinance(2L)).thenReturn(failResult);

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(1, summary.marketsProcessed);
        assertEquals(1, summary.failureCount);
        assertEquals(100, summary.totalSymbols);
    }

    @Test
    @DisplayName("同步所有交易对 - 异常处理")
    void syncAllSymbols_ExceptionHandling() {
        when(marketService.listAll(null, null, true)).thenReturn(List.of(testMarket));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolService.syncSymbolsFromBinance(1L)).thenThrow(new RuntimeException("API 错误"));

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(0, summary.marketsProcessed);
        assertEquals(1, summary.failureCount);
    }

    @Test
    @DisplayName("同步所有交易对 - 多个市场成功")
    void syncAllSymbols_MultipleMarketsSuccess() {
        MarketDTO market2 = MarketDTO.builder()
                .id(2L)
                .dataSourceId(1L)
                .name("合约市场")
                .marketType("USDT_M")
                .enabled(true)
                .build();

        SymbolSyncResult result2 = SymbolSyncResult.builder()
                .success(true)
                .syncedCount(50)
                .createdCount(5)
                .updatedCount(2)
                .build();

        when(marketService.listAll(null, null, true)).thenReturn(Arrays.asList(testMarket, market2));
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(symbolService.syncSymbolsFromBinance(1L)).thenReturn(successResult);
        when(symbolService.syncSymbolsFromBinance(2L)).thenReturn(result2);

        SymbolSyncScheduler.SymbolSyncSummary summary = symbolSyncScheduler.syncAllSymbols();

        assertEquals(2, summary.marketsProcessed);
        assertEquals(150, summary.totalSymbols);
        assertEquals(15, summary.createdCount);
        assertEquals(7, summary.updatedCount);
        assertEquals(0, summary.failureCount);
    }
}

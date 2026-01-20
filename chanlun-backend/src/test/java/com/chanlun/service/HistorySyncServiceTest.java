package com.chanlun.service;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HistorySyncService 测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HistorySyncService 测试")
class HistorySyncServiceTest {

    @Mock
    private SymbolService symbolService;

    @Mock
    private MarketService marketService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private KlineService klineService;

    @Mock
    private SyncService syncService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @Mock
    private BinanceClient binanceClient;

    @InjectMocks
    private HistorySyncService historySyncService;

    private Instant baseTime;
    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;
    private SyncTask testTask;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        
        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .historySyncEnabled(true)
                .syncIntervals("1h,4h,1d")
                .build();
        
        testMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货")
                .marketType("SPOT")
                .enabled(true)
                .build();
        
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Binance")
                .exchangeType("BINANCE")
                .baseUrl("https://api.binance.com")
                .enabled(true)
                .deleted(false)
                .build();
        
        testTask = SyncTask.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.PENDING)
                .build();
    }

    @Test
    @DisplayName("历史同步 - 成功")
    void syncHistory_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);
        
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
        when(syncService.startTask(1L)).thenReturn(true);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
        
        // Mock K线数据返回
        List<BinanceKline> mockKlines = createMockKlines(10);
        when(binanceClient.getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(BinanceApiResponse.success(mockKlines));
        
        when(klineService.batchUpsert(anyList())).thenReturn(10);
        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(endTime);
        when(syncService.completeTask(1L, 10)).thenReturn(true);
        
        int result = historySyncService.syncHistory(1L, "1h", startTime, endTime);
        
        assertEquals(10, result);
        verify(syncService).createHistoryTask(eq(1L), eq("1h"), eq(startTime), eq(endTime));
        verify(syncService).startTask(1L);
        verify(syncService).completeTask(1L, 10);
        verify(syncService).updateSyncStatus(eq(1L), eq("1h"), eq(endTime), eq(10L));
        verify(binanceClient).close();
    }

    @Test
    @DisplayName("历史同步 - symbolId为空应抛出异常")
    void syncHistory_nullSymbolId_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(null, "1h", baseTime, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("历史同步 - interval为空应抛出异常")
    void syncHistory_nullInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, null, baseTime, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("历史同步 - 不支持的周期应抛出异常")
    void syncHistory_invalidInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1s", baseTime, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("历史同步 - startTime为空应抛出异常")
    void syncHistory_nullStartTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1h", null, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("历史同步 - endTime为空应抛出异常")
    void syncHistory_nullEndTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1h", baseTime, null));
    }

    @Test
    @DisplayName("历史同步 - startTime晚于endTime应抛出异常")
    void syncHistory_invalidTimeRange_shouldThrowException() {
        Instant startTime = baseTime.plus(2, ChronoUnit.DAYS);
        Instant endTime = baseTime;
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1h", startTime, endTime));
    }

    @Test
    @DisplayName("历史同步 - 数据源未启用应抛出异常")
    void syncHistory_dataSourceDisabled_shouldThrowException() {
        testDataSource.setEnabled(false);
        
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1h", baseTime, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("历史同步 - 市场未启用应抛出异常")
    void syncHistory_marketDisabled_shouldThrowException() {
        testMarket.setEnabled(false);
        
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        
        assertThrows(BusinessException.class, 
                () -> historySyncService.syncHistory(1L, "1h", baseTime, baseTime.plus(1, ChronoUnit.DAYS)));
    }

    @Test
    @DisplayName("增量同步 - 首次启用仅补前一日")
    void syncIncremental_firstTime_shouldSyncLastDay() {
        when(syncService.getSyncStatus(1L, "1h")).thenReturn(null);
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
        when(syncService.startTask(1L)).thenReturn(true);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
        
        List<BinanceKline> mockKlines = createMockKlines(5);
        when(binanceClient.getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(BinanceApiResponse.success(mockKlines));
        
        when(klineService.batchUpsert(anyList())).thenReturn(5);
        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(Instant.now());
        when(syncService.completeTask(1L, 5)).thenReturn(true);
        
        int result = historySyncService.syncIncremental(1L, "1h");
        
        assertEquals(5, result);
        // 验证创建任务时的时间范围是前一天
        verify(syncService).createHistoryTask(eq(1L), eq("1h"), argThat(start -> {
            // 开始时间应该是大约1天前
            Duration diff = Duration.between(start, Instant.now());
            return diff.toDays() >= 0 && diff.toDays() <= 1;
        }), any());
    }

    @Test
    @DisplayName("增量同步 - 已有数据从lastKlineTime继续")
    void syncIncremental_hasData_shouldContinueFromLastKlineTime() {
        Instant lastKlineTime = Instant.now().minus(2, ChronoUnit.HOURS);
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .lastKlineTime(lastKlineTime)
                .totalKlines(100L)
                .build();
        
        when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
        when(syncService.startTask(1L)).thenReturn(true);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
        
        List<BinanceKline> mockKlines = createMockKlines(2);
        when(binanceClient.getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(BinanceApiResponse.success(mockKlines));
        
        when(klineService.batchUpsert(anyList())).thenReturn(2);
        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(Instant.now());
        when(syncService.completeTask(1L, 2)).thenReturn(true);
        
        int result = historySyncService.syncIncremental(1L, "1h");
        
        assertEquals(2, result);
    }

    @Test
    @DisplayName("增量同步 - 已是最新无需同步")
    void syncIncremental_alreadyUpToDate_shouldReturnZero() {
        // lastKlineTime 是当前时间，无需同步
        Instant lastKlineTime = Instant.now();
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .lastKlineTime(lastKlineTime)
                .totalKlines(100L)
                .build();
        
        when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
        
        int result = historySyncService.syncIncremental(1L, "1h");
        
        assertEquals(0, result);
        verify(syncService, never()).createHistoryTask(anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("获取周期毫秒数 - 1m")
    void getIntervalMillis_1m() {
        assertEquals(60_000L, historySyncService.getIntervalMillis("1m"));
    }

    @Test
    @DisplayName("获取周期毫秒数 - 1h")
    void getIntervalMillis_1h() {
        assertEquals(3600_000L, historySyncService.getIntervalMillis("1h"));
    }

    @Test
    @DisplayName("获取周期毫秒数 - 1d")
    void getIntervalMillis_1d() {
        assertEquals(86400_000L, historySyncService.getIntervalMillis("1d"));
    }

    @Test
    @DisplayName("批量增量同步 - 成功")
    void syncAllIncremental_success() {
        List<SymbolDTO> symbols = List.of(
                SymbolDTO.builder()
                        .id(1L)
                        .symbol("BTCUSDT")
                        .syncIntervals(List.of("1h"))
                        .build()
        );
        
        when(symbolService.getHistorySyncEnabled()).thenReturn(symbols);
        when(syncService.getSyncStatus(1L, "1h")).thenReturn(null);
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
        when(syncService.startTask(1L)).thenReturn(true);
        when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
        
        List<BinanceKline> mockKlines = createMockKlines(5);
        when(binanceClient.getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(BinanceApiResponse.success(mockKlines));
        
        when(klineService.batchUpsert(anyList())).thenReturn(5);
        when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(Instant.now());
        when(syncService.completeTask(1L, 5)).thenReturn(true);
        
        HistorySyncService.IncrementalSyncSummary summary = historySyncService.syncAllIncremental();
        
        assertEquals(1, summary.getTotalSymbols());
        assertEquals(1, summary.getSuccessCount());
        assertEquals(0, summary.getFailureCount());
        assertEquals(5, summary.getTotalKlines());
    }

    @Test
    @DisplayName("批量增量同步 - 无配置周期跳过")
    void syncAllIncremental_noIntervals_shouldSkip() {
        List<SymbolDTO> symbols = List.of(
                SymbolDTO.builder()
                        .id(1L)
                        .symbol("BTCUSDT")
                        .syncIntervals(null)
                        .build()
        );
        
        when(symbolService.getHistorySyncEnabled()).thenReturn(symbols);
        
        HistorySyncService.IncrementalSyncSummary summary = historySyncService.syncAllIncremental();
        
        assertEquals(0, summary.getTotalSymbols());
        verify(syncService, never()).createHistoryTask(anyLong(), anyString(), any(), any());
    }

    /**
     * 创建 Mock K线数据
     */
    private List<BinanceKline> createMockKlines(int count) {
        List<BinanceKline> klines = new ArrayList<>();
        long startTime = baseTime.toEpochMilli();
        
        for (int i = 0; i < count; i++) {
            klines.add(BinanceKline.builder()
                    .openTime(startTime + i * 3600_000L)
                    .open(BigDecimal.valueOf(50000 + i * 100))
                    .high(BigDecimal.valueOf(50100 + i * 100))
                    .low(BigDecimal.valueOf(49900 + i * 100))
                    .close(BigDecimal.valueOf(50050 + i * 100))
                    .volume(BigDecimal.valueOf(1000))
                    .closeTime(startTime + (i + 1) * 3600_000L - 1)
                    .quoteVolume(BigDecimal.valueOf(50000000))
                    .trades(500)
                    .takerBuyBaseVolume(BigDecimal.valueOf(500))
                    .takerBuyQuoteVolume(BigDecimal.valueOf(25000000))
                    .build());
        }
        
        return klines;
    }
}

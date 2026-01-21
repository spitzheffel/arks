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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * 历史同步增量策略与时间校验测试
 * 
 * 覆盖任务 29.1：
 * - 增量同步策略（首次启用、基于 last_kline_time 追赶）
 * - 时间范围校验
 * - 分段同步逻辑（单次不超过30天）
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("历史同步增量策略与时间校验测试")
class HistorySyncIncrementalStrategyTest {

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

    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;
    private SyncTask testTask;

    @BeforeEach
    void setUp() {
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

    @Nested
    @DisplayName("增量同步策略测试")
    class IncrementalSyncStrategyTest {

        @Test
        @DisplayName("首次启用 - last_kline_time 为 NULL - 仅补前一日数据")
        void syncIncremental_firstTimeWithNullLastKlineTime_shouldSyncOneDayOnly() {
            // 模拟首次启用，sync_status 不存在
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(null);
            
            setupSuccessfulSync();
            
            historySyncService.syncIncremental(1L, "1h");
            
            // 验证创建任务时的时间范围
            ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(syncService).createHistoryTask(eq(1L), eq("1h"), startCaptor.capture(), endCaptor.capture());
            
            Instant start = startCaptor.getValue();
            Instant end = endCaptor.getValue();
            
            // 验证时间范围约为1天
            Duration duration = Duration.between(start, end);
            assertTrue(duration.toHours() >= 23 && duration.toHours() <= 25, 
                    "首次启用应仅补前一日数据，实际时间跨度: " + duration.toHours() + " 小时");
        }

        @Test
        @DisplayName("首次启用 - last_kline_time 为 NULL 的 SyncStatus - 仅补前一日数据")
        void syncIncremental_firstTimeWithNullLastKlineTimeInStatus_shouldSyncOneDayOnly() {
            // 模拟 sync_status 存在但 last_kline_time 为 NULL
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(null)  // NULL 表示无数据
                    .totalKlines(0L)
                    .build();
            
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
            
            setupSuccessfulSync();
            
            historySyncService.syncIncremental(1L, "1h");
            
            // 验证创建任务时的时间范围
            ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(syncService).createHistoryTask(eq(1L), eq("1h"), startCaptor.capture(), endCaptor.capture());
            
            Instant start = startCaptor.getValue();
            Instant end = endCaptor.getValue();
            
            // 验证时间范围约为1天
            Duration duration = Duration.between(start, end);
            assertTrue(duration.toHours() >= 23 && duration.toHours() <= 25, 
                    "首次启用应仅补前一日数据，实际时间跨度: " + duration.toHours() + " 小时");
        }

        @Test
        @DisplayName("增量追赶 - 从 last_kline_time 的下一根 K 线开始")
        void syncIncremental_withLastKlineTime_shouldStartFromNextKline() {
            Instant lastKlineTime = Instant.now().minus(5, ChronoUnit.HOURS);
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(lastKlineTime)
                    .totalKlines(100L)
                    .build();
            
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
            
            setupSuccessfulSync();
            
            historySyncService.syncIncremental(1L, "1h");
            
            // 验证开始时间是 last_kline_time + 1小时
            ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(syncService).createHistoryTask(eq(1L), eq("1h"), startCaptor.capture(), any());
            
            Instant expectedStart = lastKlineTime.plusMillis(3600_000L); // +1小时
            assertEquals(expectedStart, startCaptor.getValue(), 
                    "应从 last_kline_time 的下一根 K 线开始");
        }

        @Test
        @DisplayName("增量追赶 - 已是最新数据 - 不执行同步")
        void syncIncremental_alreadyUpToDate_shouldSkipSync() {
            // last_kline_time 是当前时间或未来时间
            Instant lastKlineTime = Instant.now().plus(1, ChronoUnit.HOURS);
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(lastKlineTime)
                    .totalKlines(100L)
                    .build();
            
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
            
            int result = historySyncService.syncIncremental(1L, "1h");
            
            assertEquals(0, result, "已是最新数据，应返回0");
            verify(syncService, never()).createHistoryTask(anyLong(), anyString(), any(), any());
        }

        @Test
        @DisplayName("增量追赶 - 跨度超过30天 - 应分段同步")
        void syncIncremental_largeGap_shouldSyncInSegments() {
            // 模拟停机60天的场景
            Instant lastKlineTime = Instant.now().minus(60, ChronoUnit.DAYS);
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(lastKlineTime)
                    .totalKlines(100L)
                    .build();
            
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
            
            setupSuccessfulSync();
            
            historySyncService.syncIncremental(1L, "1h");
            
            // 验证调用了多次 getKlines（分段拉取）
            verify(binanceClient, atLeast(2)).getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt());
        }
    }

    @Nested
    @DisplayName("时间范围校验测试")
    class TimeRangeValidationTest {

        @Test
        @DisplayName("startTime 为 NULL - 应抛出异常")
        void syncHistory_nullStartTime_shouldThrowException() {
            Instant endTime = Instant.now();
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "1h", null, endTime));
            
            assertTrue(exception.getMessage().contains("开始时间不能为空"));
        }

        @Test
        @DisplayName("endTime 为 NULL - 应抛出异常")
        void syncHistory_nullEndTime_shouldThrowException() {
            Instant startTime = Instant.now().minus(1, ChronoUnit.DAYS);
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "1h", startTime, null));
            
            assertTrue(exception.getMessage().contains("结束时间不能为空"));
        }

        @Test
        @DisplayName("startTime 晚于 endTime - 应抛出异常")
        void syncHistory_startAfterEnd_shouldThrowException() {
            Instant startTime = Instant.now();
            Instant endTime = startTime.minus(1, ChronoUnit.DAYS);
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "1h", startTime, endTime));
            
            assertTrue(exception.getMessage().contains("开始时间不能晚于结束时间"));
        }

        @Test
        @DisplayName("startTime 晚于当前时间 - 应抛出异常")
        void syncHistory_startInFuture_shouldThrowException() {
            Instant startTime = Instant.now().plus(1, ChronoUnit.DAYS);
            Instant endTime = startTime.plus(1, ChronoUnit.DAYS);
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "1h", startTime, endTime));
            
            assertTrue(exception.getMessage().contains("开始时间不能晚于当前时间"));
        }

        @Test
        @DisplayName("不支持的周期 - 应抛出异常")
        void syncHistory_invalidInterval_shouldThrowException() {
            Instant startTime = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "1s", startTime, endTime));
            
            assertTrue(exception.getMessage().contains("不支持的时间周期"));
        }

        @Test
        @DisplayName("周期为空字符串 - 应抛出异常")
        void syncHistory_emptyInterval_shouldThrowException() {
            Instant startTime = Instant.now().minus(1, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            BusinessException exception = assertThrows(BusinessException.class, 
                    () -> historySyncService.syncHistory(1L, "", startTime, endTime));
            
            assertTrue(exception.getMessage().contains("时间周期不能为空"));
        }
    }

    @Nested
    @DisplayName("分段同步逻辑测试")
    class SegmentedSyncTest {

        @Test
        @DisplayName("单次跨度不超过30天 - 小于30天 - 单次同步")
        void syncHistory_lessThan30Days_shouldSyncOnce() {
            Instant startTime = Instant.now().minus(20, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            setupSuccessfulSync();
            
            historySyncService.syncHistory(1L, "1h", startTime, endTime);
            
            // 验证只调用了一次 getKlines
            verify(binanceClient, times(1)).getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("单次跨度不超过30天 - 正好30天 - 单次同步")
        void syncHistory_exactly30Days_shouldSyncOnce() {
            Instant startTime = Instant.now().minus(30, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            setupSuccessfulSync();
            
            historySyncService.syncHistory(1L, "1h", startTime, endTime);
            
            // 验证只调用了一次 getKlines
            verify(binanceClient, times(1)).getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("单次跨度不超过30天 - 超过30天 - 分段同步")
        void syncHistory_moreThan30Days_shouldSyncInSegments() {
            Instant startTime = Instant.now().minus(45, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            setupSuccessfulSync();
            
            historySyncService.syncHistory(1L, "1h", startTime, endTime);
            
            // 验证调用了多次 getKlines（至少2次）
            verify(binanceClient, atLeast(2)).getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt());
        }

        @Test
        @DisplayName("单次跨度不超过30天 - 90天 - 分3段同步")
        void syncHistory_90Days_shouldSyncIn3Segments() {
            Instant startTime = Instant.now().minus(90, ChronoUnit.DAYS);
            Instant endTime = Instant.now();
            
            setupSuccessfulSync();
            
            historySyncService.syncHistory(1L, "1h", startTime, endTime);
            
            // 验证调用了至少3次 getKlines
            verify(binanceClient, atLeast(3)).getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt());
        }
    }

    @Nested
    @DisplayName("周期毫秒数计算测试")
    class IntervalMillisTest {

        @Test
        @DisplayName("1m = 60,000 毫秒")
        void getIntervalMillis_1m() {
            assertEquals(60_000L, historySyncService.getIntervalMillis("1m"));
        }

        @Test
        @DisplayName("3m = 180,000 毫秒")
        void getIntervalMillis_3m() {
            assertEquals(180_000L, historySyncService.getIntervalMillis("3m"));
        }

        @Test
        @DisplayName("5m = 300,000 毫秒")
        void getIntervalMillis_5m() {
            assertEquals(300_000L, historySyncService.getIntervalMillis("5m"));
        }

        @Test
        @DisplayName("15m = 900,000 毫秒")
        void getIntervalMillis_15m() {
            assertEquals(900_000L, historySyncService.getIntervalMillis("15m"));
        }

        @Test
        @DisplayName("30m = 1,800,000 毫秒")
        void getIntervalMillis_30m() {
            assertEquals(1_800_000L, historySyncService.getIntervalMillis("30m"));
        }

        @Test
        @DisplayName("1h = 3,600,000 毫秒")
        void getIntervalMillis_1h() {
            assertEquals(3_600_000L, historySyncService.getIntervalMillis("1h"));
        }

        @Test
        @DisplayName("2h = 7,200,000 毫秒")
        void getIntervalMillis_2h() {
            assertEquals(7_200_000L, historySyncService.getIntervalMillis("2h"));
        }

        @Test
        @DisplayName("4h = 14,400,000 毫秒")
        void getIntervalMillis_4h() {
            assertEquals(14_400_000L, historySyncService.getIntervalMillis("4h"));
        }

        @Test
        @DisplayName("1d = 86,400,000 毫秒")
        void getIntervalMillis_1d() {
            assertEquals(86_400_000L, historySyncService.getIntervalMillis("1d"));
        }

        @Test
        @DisplayName("1w = 604,800,000 毫秒")
        void getIntervalMillis_1w() {
            assertEquals(604_800_000L, historySyncService.getIntervalMillis("1w"));
        }
    }

    /**
     * 设置成功同步的 Mock
     */
    private void setupSuccessfulSync() {
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(marketService.findById(1L)).thenReturn(testMarket);
        when(dataSourceService.findById(1L)).thenReturn(testDataSource);
        when(syncService.createHistoryTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
        when(syncService.startTask(anyLong())).thenReturn(true);
        when(binanceClientFactory.createClient(any())).thenReturn(binanceClient);
        
        List<BinanceKline> mockKlines = createMockKlines(10);
        when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                .thenReturn(BinanceApiResponse.success(mockKlines));
        
        when(klineService.batchUpsert(anyList())).thenReturn(10);
        when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(Instant.now());
        when(syncService.completeTask(anyLong(), anyInt())).thenReturn(true);
    }

    /**
     * 创建 Mock K线数据
     */
    private List<BinanceKline> createMockKlines(int count) {
        List<BinanceKline> klines = new ArrayList<>();
        long startTime = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
        
        for (int i = 0; i < count; i++) {
            klines.add(BinanceKline.builder()
                    .openTime(startTime + i * 3600_000L)
                    .open(BigDecimal.valueOf(50000))
                    .high(BigDecimal.valueOf(50100))
                    .low(BigDecimal.valueOf(49900))
                    .close(BigDecimal.valueOf(50050))
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

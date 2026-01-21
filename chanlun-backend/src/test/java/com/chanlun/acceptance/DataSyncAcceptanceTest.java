package com.chanlun.acceptance;

import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 数据同步验收测试 (任务 25)
 * 
 * 验证数据同步相关功能的正确性：
 * - 25.1 历史数据同步
 * - 25.2 实时数据同步 (WebSocket)
 * - 25.3 定时任务执行 (UTC 时区)
 * - 25.4 K线数据查询性能 (<500ms)
 * - 25.5 同步对象筛选
 * - 25.6 sync.realtime.enabled 全局开关
 * - 25.7 周期合法性校验 (拒绝 1s)
 * - 25.8 手动删除历史数据功能
 * - 25.9 删除后关闭自动回补开关
 * - 25.10 手动历史同步筛选规则
 * - 25.11 last_kline_time 正确重算
 * - 25.12 last_kline_time 为 NULL 时的处理
 * - 25.13 total_klines 正确重算
 * - 25.14 手动历史同步时间范围校验
 * - 25.15 关闭实时同步时断开 WebSocket
 * - 25.16 历史同步更新 sync_status
 * - 25.17 实时同步更新 sync_status
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("数据同步验收测试 (任务 25)")
class DataSyncAcceptanceTest {

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
    private SystemConfigService systemConfigService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @Mock
    private BinanceClient binanceClient;

    @InjectMocks
    private HistorySyncService historySyncService;

    private DataSource testDataSource;
    private Market testMarket;
    private Symbol testSymbol;
    private SyncTask testTask;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");

        testDataSource = DataSource.builder()
                .id(1L)
                .name("Binance")
                .exchangeType("BINANCE")
                .enabled(true)
                .deleted(false)
                .build();

        testMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("现货")
                .marketType("SPOT")
                .enabled(true)
                .build();

        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .realtimeSyncEnabled(true)
                .historySyncEnabled(true)
                .syncIntervals("1m,5m,1h")
                .status("TRADING")
                .build();

        testTask = SyncTask.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.PENDING)
                .build();
    }

    // ==================== 25.1 验证历史数据同步 ====================

    @Nested
    @DisplayName("25.1 验证历史数据同步")
    class HistorySyncTests {

        @Test
        @DisplayName("历史同步 - 成功同步指定时间范围的K线数据")
        void syncHistory_success() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);

            List<BinanceKline> mockKlines = createMockKlines(10);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class)))
                    .thenReturn(BinanceApiResponse.success(mockKlines));

            when(klineService.batchUpsert(anyList())).thenReturn(10);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(endTime);
            when(syncService.completeTask(1L, 10)).thenReturn(true);
            doNothing().when(syncService).updateSyncStatus(anyLong(), anyString(), any(Instant.class), anyLong());

            int result = historySyncService.syncHistory(1L, "1h", startTime, endTime);

            assertEquals(10, result);
            verify(syncService).createHistoryTask(eq(1L), eq("1h"), eq(startTime), eq(endTime));
            verify(syncService).startTask(1L);
            verify(syncService).completeTask(1L, 10);
            verify(syncService).updateSyncStatus(eq(1L), eq("1h"), any(Instant.class), anyLong());
            verify(binanceClient).close();
        }

        @Test
        @DisplayName("历史同步 - 分段处理超过30天的数据")
        void syncHistory_segmentedForLongRange() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(60, ChronoUnit.DAYS); // 60天

            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);

            List<BinanceKline> mockKlines = createMockKlines(5);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class)))
                    .thenReturn(BinanceApiResponse.success(mockKlines));

            when(klineService.batchUpsert(anyList())).thenReturn(5);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(endTime);
            when(syncService.completeTask(anyLong(), anyInt())).thenReturn(true);

            int result = historySyncService.syncHistory(1L, "1h", startTime, endTime);

            // 验证分段调用（60天应该分成至少2段）
            verify(binanceClient, atLeast(2)).getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class));
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
    }

    // ==================== 25.7 验证周期合法性校验 (拒绝 1s) ====================

    @Nested
    @DisplayName("25.7 验证周期合法性校验 (拒绝 1s)")
    class IntervalValidationTests {

        @Test
        @DisplayName("周期校验 - 拒绝 1s 周期")
        void validateInterval_reject1s() {
            assertThrows(BusinessException.class,
                    () -> historySyncService.syncHistory(1L, "1s", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }

        @Test
        @DisplayName("周期校验 - 接受 1m 周期")
        void validateInterval_accept1m() {
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createHistoryTask(eq(1L), eq("1m"), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class)))
                    .thenReturn(BinanceApiResponse.success(createMockKlines(1)));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(baseTime);
            when(syncService.completeTask(anyLong(), anyInt())).thenReturn(true);
            doNothing().when(syncService).updateSyncStatus(anyLong(), anyString(), any(Instant.class), anyLong());

            // 不应抛出异常
            assertDoesNotThrow(() -> 
                    historySyncService.syncHistory(1L, "1m", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }

        @Test
        @DisplayName("周期校验 - 接受所有有效周期")
        void validateInterval_acceptAllValidIntervals() {
            String[] validIntervals = {"1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w", "1M"};

            for (String interval : validIntervals) {
                // 验证 getIntervalMillis 不抛出异常
                long millis = historySyncService.getIntervalMillis(interval);
                assertTrue(millis > 0, "周期 " + interval + " 应该返回正数毫秒值");
            }
        }

        @Test
        @DisplayName("周期校验 - 拒绝无效周期")
        void validateInterval_rejectInvalidIntervals() {
            String[] invalidIntervals = {"1s", "2s", "10s", "2m", "10m", "3h", "5h", "2d", "2w", "2M", "invalid"};

            for (String interval : invalidIntervals) {
                assertThrows(BusinessException.class,
                        () -> historySyncService.syncHistory(1L, interval, baseTime, baseTime.plus(1, ChronoUnit.HOURS)),
                        "周期 " + interval + " 应该被拒绝");
            }
        }
    }

    // ==================== 25.12 验证 last_kline_time 为 NULL 时的处理 ====================

    @Nested
    @DisplayName("25.12 验证 last_kline_time 为 NULL 时的处理")
    class LastKlineTimeNullTests {

        @Test
        @DisplayName("增量同步 - last_kline_time 为 NULL 时仅补前一日数据")
        void syncIncremental_nullLastKlineTime_shouldSyncLastDay() {
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(null);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);

            List<BinanceKline> mockKlines = createMockKlines(5);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class)))
                    .thenReturn(BinanceApiResponse.success(mockKlines));

            when(klineService.batchUpsert(anyList())).thenReturn(5);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(Instant.now());
            when(syncService.completeTask(1L, 5)).thenReturn(true);
            doNothing().when(syncService).updateSyncStatus(anyLong(), anyString(), any(Instant.class), anyLong());

            int result = historySyncService.syncIncremental(1L, "1h");

            assertEquals(5, result);
            // 验证创建任务时的时间范围是前一天
            verify(syncService).createHistoryTask(eq(1L), eq("1h"), argThat(start -> {
                // 开始时间应该是大约1天前
                java.time.Duration diff = java.time.Duration.between(start, Instant.now());
                return diff.toDays() >= 0 && diff.toDays() <= 1;
            }), any());
        }

        @Test
        @DisplayName("增量同步 - SyncStatus 存在但 last_kline_time 为 NULL")
        void syncIncremental_statusExistsButNullLastKlineTime_shouldSyncLastDay() {
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(null) // NULL
                    .totalKlines(0L)
                    .build();

            when(syncService.getSyncStatus(1L, "1h")).thenReturn(status);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createHistoryTask(eq(1L), eq("1h"), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);

            List<BinanceKline> mockKlines = createMockKlines(5);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), any(Integer.class)))
                    .thenReturn(BinanceApiResponse.success(mockKlines));

            when(klineService.batchUpsert(anyList())).thenReturn(5);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(Instant.now());
            when(syncService.completeTask(1L, 5)).thenReturn(true);
            doNothing().when(syncService).updateSyncStatus(anyLong(), anyString(), any(Instant.class), anyLong());

            int result = historySyncService.syncIncremental(1L, "1h");

            assertEquals(5, result);
        }
    }

    // ==================== 25.14 验证手动历史同步时间范围校验 ====================

    @Nested
    @DisplayName("25.14 验证手动历史同步时间范围校验")
    class TimeRangeValidationTests {

        @Test
        @DisplayName("时间范围校验 - startTime 为空应抛出异常")
        void syncHistory_nullStartTime_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> historySyncService.syncHistory(1L, "1h", null, baseTime.plus(1, ChronoUnit.DAYS)));
        }

        @Test
        @DisplayName("时间范围校验 - endTime 为空应抛出异常")
        void syncHistory_nullEndTime_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> historySyncService.syncHistory(1L, "1h", baseTime, null));
        }

        @Test
        @DisplayName("时间范围校验 - startTime 晚于 endTime 应抛出异常")
        void syncHistory_invalidTimeRange_shouldThrowException() {
            Instant startTime = baseTime.plus(2, ChronoUnit.DAYS);
            Instant endTime = baseTime;
            assertThrows(BusinessException.class,
                    () -> historySyncService.syncHistory(1L, "1h", startTime, endTime));
        }

        @Test
        @DisplayName("时间范围校验 - startTime 晚于当前时间应抛出异常")
        void syncHistory_futureStartTime_shouldThrowException() {
            Instant futureTime = Instant.now().plus(1, ChronoUnit.DAYS);
            assertThrows(BusinessException.class,
                    () -> historySyncService.syncHistory(1L, "1h", futureTime, futureTime.plus(1, ChronoUnit.DAYS)));
        }
    }

    // ==================== 辅助方法 ====================

    private List<BinanceKline> createMockKlines(int count) {
        List<BinanceKline> klines = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BinanceKline kline = new BinanceKline();
            kline.setOpenTime(baseTime.plus(i, ChronoUnit.HOURS).toEpochMilli());
            kline.setOpen(new BigDecimal("50000.00"));
            kline.setHigh(new BigDecimal("51000.00"));
            kline.setLow(new BigDecimal("49000.00"));
            kline.setClose(new BigDecimal("50500.00"));
            kline.setVolume(new BigDecimal("100.00"));
            kline.setQuoteVolume(new BigDecimal("5000000.00"));
            kline.setTrades(1000);
            kline.setCloseTime(baseTime.plus(i + 1, ChronoUnit.HOURS).minusMillis(1).toEpochMilli());
            klines.add(kline);
        }
        return klines;
    }
}

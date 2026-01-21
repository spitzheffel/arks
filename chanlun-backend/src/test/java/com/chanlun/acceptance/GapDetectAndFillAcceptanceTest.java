package com.chanlun.acceptance;

import com.chanlun.dto.GapDetectResult;
import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.*;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import com.chanlun.mapper.SyncTaskMapper;
import com.chanlun.service.*;
import com.chanlun.service.GapFillService.BatchGapFillResult;
import com.chanlun.service.GapFillService.GapFillResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缺口检测与回补验收测试 (任务 26)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("缺口检测与回补验收测试")
class GapDetectAndFillAcceptanceTest {

    @Mock private DataGapMapper dataGapMapper;
    @Mock private KlineMapper klineMapper;
    @Mock private SyncStatusMapper syncStatusMapper;
    @Mock private SyncTaskMapper syncTaskMapper;
    @Mock private SymbolService symbolService;
    @Mock private MarketService marketService;
    @Mock private DataSourceService dataSourceService;
    @Mock private SyncFilterService syncFilterService;
    @Mock private KlineService klineService;
    @Mock private SyncService syncService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private BinanceClientFactory binanceClientFactory;
    @Mock private BinanceClient binanceClient;
    @Mock private DataGapService dataGapServiceMock;

    @InjectMocks private DataGapService dataGapService;

    private GapFillService gapFillService;

    private Instant baseTime;
    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;
    private DataGap testGap;
    private SyncTask testTask;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        testSymbol = Symbol.builder()
                .id(1L).marketId(1L).symbol("BTCUSDT")
                .historySyncEnabled(true).syncIntervals("1m,1h,1d").build();
        testMarket = Market.builder()
                .id(1L).dataSourceId(1L).name("Spot").enabled(true).build();
        testDataSource = DataSource.builder()
                .id(1L).name("Binance").exchangeType("BINANCE").enabled(true).deleted(false).build();
        testGap = DataGap.builder()
                .id(1L).symbolId(1L).interval("1h")
                .gapStart(baseTime).gapEnd(baseTime.plus(2, ChronoUnit.HOURS))
                .missingCount(2).status("PENDING").retryCount(0).build();
        testTask = SyncTask.builder()
                .id(1L).symbolId(1L).interval("1h").taskType("GAP_FILL").status("PENDING").build();
        
        // 初始化 GapFillService - 使用 mock 的 DataGapService
        gapFillService = new GapFillService(dataGapMapper, dataGapServiceMock, symbolService, 
                marketService, dataSourceService, klineService, syncService, 
                systemConfigService, binanceClientFactory);
    }

    private Kline createKline(Long symbolId, String interval, Instant openTime) {
        return Kline.builder().id(1L).symbolId(symbolId).interval(interval)
                .openTime(openTime).open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00")).low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00")).volume(new BigDecimal("1000.00"))
                .closeTime(openTime.plusMillis(3600000 - 1)).build();
    }

    private DataGap createDataGap(Long symbolId, String interval, Instant gapStart, 
            Instant gapEnd, int missingCount, String status) {
        return DataGap.builder().id(1L).symbolId(symbolId).interval(interval)
                .gapStart(gapStart).gapEnd(gapEnd).missingCount(missingCount)
                .status(status).retryCount(0).createdAt(Instant.now())
                .updatedAt(Instant.now()).build();
    }

    private SymbolDTO createSymbolDTO(Long id, String symbol, List<String> intervals) {
        return SymbolDTO.builder().id(id).marketId(1L).marketName("Spot")
                .dataSourceId(1L).dataSourceName("Binance").symbol(symbol)
                .historySyncEnabled(true).syncIntervals(intervals).build();
    }

    private BinanceKline createBinanceKline(long openTime) {
        return BinanceKline.builder()
                .openTime(openTime).open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00")).low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00")).volume(new BigDecimal("1000.00"))
                .closeTime(openTime + 3600000 - 1).quoteVolume(new BigDecimal("50000000.00"))
                .trades(100).build();
    }

    // ==================== 26.1 验证缺口检测准确性 ====================

    @Nested
    @DisplayName("26.1 验证缺口检测准确性")
    class GapDetectionAccuracyTests {

        @Test
        @DisplayName("连续K线数据无缺口")
        void continuousKlines_noGapsDetected() {
            List<Kline> klines = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                klines.add(createKline(1L, "1h", baseTime.plus(i, ChronoUnit.HOURS)));
            }
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(Collections.emptyList());

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertTrue(result.isSuccess());
            assertEquals(0, result.getNewGapCount());
        }

        @Test
        @DisplayName("单个缺口正确检测")
        void singleGap_correctlyDetected() {
            List<Kline> klines = List.of(
                    createKline(1L, "1h", baseTime),
                    createKline(1L, "1h", baseTime.plus(3, ChronoUnit.HOURS)));
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dataGapMapper.batchInsert(anyList())).thenReturn(1);
            when(dataGapMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(Collections.emptyList());

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertTrue(result.isSuccess());
            assertEquals(1, result.getNewGapCount());
        }

        @Test
        @DisplayName("多个缺口正确检测")
        void multipleGaps_correctlyDetected() {
            List<Kline> klines = List.of(
                    createKline(1L, "1h", baseTime),
                    createKline(1L, "1h", baseTime.plus(3, ChronoUnit.HOURS)),
                    createKline(1L, "1h", baseTime.plus(6, ChronoUnit.HOURS)));
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(dataGapMapper.batchInsert(anyList())).thenReturn(2);
            when(dataGapMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(Collections.emptyList());

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertTrue(result.isSuccess());
            assertEquals(2, result.getNewGapCount());
        }

        @Test
        @DisplayName("已存在重叠缺口时不重复创建")
        void overlappingGapExists_notDuplicated() {
            List<Kline> klines = List.of(
                    createKline(1L, "1h", baseTime),
                    createKline(1L, "1h", baseTime.plus(3, ChronoUnit.HOURS)));
            DataGap existingGap = createDataGap(1L, "1h", 
                    baseTime.plus(1, ChronoUnit.HOURS),
                    baseTime.plus(2, ChronoUnit.HOURS), 2, "PENDING");
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of(existingGap));
            when(dataGapMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(List.of(existingGap));

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertTrue(result.isSuccess());
            assertEquals(0, result.getNewGapCount());
            verify(dataGapMapper, never()).batchInsert(anyList());
        }
    }

    // ==================== 26.2 验证缺口回补功能 ====================

    @Nested
    @DisplayName("26.2 验证缺口回补功能")
    class GapFillFunctionTests {

        @Test
        @DisplayName("成功回补单个缺口")
        void fillSingleGap_success() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(
                    createBinanceKline(baseTime.toEpochMilli()),
                    createBinanceKline(baseTime.plus(1, ChronoUnit.HOURS).toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(2);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(baseTime.plus(1, ChronoUnit.HOURS));
            
            GapFillResult result = gapFillService.fillGap(1L);
            
            assertTrue(result.isSuccess());
            assertEquals(2, result.getSyncedCount());
            verify(dataGapServiceMock).updateStatus(1L, "FILLING");
            verify(dataGapServiceMock).updateStatus(1L, "FILLED");
        }

        @Test
        @DisplayName("回补失败后状态流转正确")
        void fillGap_failure_statusTransition() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.error(-1, "API Error"));
            when(systemConfigService.getGapFillMaxRetry()).thenReturn(3);
            
            GapFillResult result = gapFillService.fillGap(1L);
            
            assertFalse(result.isSuccess());
            verify(dataGapServiceMock).updateStatus(1L, "FILLING");
            verify(dataGapServiceMock).incrementRetryCount(1L);
            verify(syncService).failTask(eq(1L), anyString());
        }

        @Test
        @DisplayName("批量回补多个缺口")
        void batchFillGaps_multipleGaps() {
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            when(dataGapServiceMock.findById(anyLong())).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(anyLong())).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(createBinanceKline(baseTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(baseTime);
            
            BatchGapFillResult result = gapFillService.batchFillGaps(List.of(1L, 2L));
            
            assertEquals(2, result.getTotalCount());
            assertEquals(2, result.getSuccessCount());
        }
    }


    // ==================== 26.4 验证缺口检测筛选条件 ====================

    @Nested
    @DisplayName("26.4 验证缺口检测筛选条件")
    class GapDetectFilterTests {

        @Test
        @DisplayName("历史同步未启用时不检测缺口")
        void historySyncDisabled_noDetection() {
            testSymbol.setHistorySyncEnabled(false);
            when(symbolService.findById(1L)).thenReturn(testSymbol);

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("不符合"));
        }

        @Test
        @DisplayName("周期未配置时不检测缺口")
        void intervalNotConfigured_noDetection() {
            testSymbol.setSyncIntervals("1d");
            when(symbolService.findById(1L)).thenReturn(testSymbol);

            GapDetectResult result = dataGapService.detectGaps(1L, "1h");

            assertFalse(result.isSuccess());
        }

        @Test
        @DisplayName("批量检测仅处理符合条件的交易对")
        void batchDetect_onlyEligibleSymbols() {
            var symbol1 = createSymbolDTO(1L, "BTCUSDT", List.of("1h"));
            var symbol2 = createSymbolDTO(2L, "ETHUSDT", List.of("1h"));
            when(syncFilterService.getGapDetectTargets()).thenReturn(List.of(symbol1, symbol2));
            when(syncFilterService.getValidSyncIntervals(symbol1)).thenReturn(List.of("1h"));
            when(syncFilterService.getValidSyncIntervals(symbol2)).thenReturn(List.of("1h"));

            List<Kline> klines = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                klines.add(createKline(1L, "1h", baseTime.plus(i, ChronoUnit.HOURS)));
            }
            when(klineMapper.selectBySymbolIdAndInterval(anyLong(), anyString())).thenReturn(klines);
            when(dataGapMapper.countByStatus(anyString())).thenReturn(0L);

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(2, result.getSymbolCount());
            assertEquals(2, result.getIntervalCount());
        }

        @Test
        @DisplayName("无符合条件的交易对时返回空结果")
        void noEligibleSymbols_emptyResult() {
            when(syncFilterService.getGapDetectTargets()).thenReturn(Collections.emptyList());

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(0, result.getSymbolCount());
            assertEquals("没有符合条件的交易对", result.getMessage());
        }
    }

    // ==================== 26.3 验证自动回补限流策略 ====================

    @Nested
    @DisplayName("26.3 验证自动回补限流策略")
    class AutoFillRateLimitTests {

        @Test
        @DisplayName("批量回补按配置间隔执行")
        void batchFill_respectsIntervalConfig() {
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(50L);
            when(dataGapServiceMock.findById(anyLong())).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(anyLong())).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(createBinanceKline(baseTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(baseTime);
            
            long startTime = System.currentTimeMillis();
            gapFillService.batchFillGaps(List.of(1L, 2L, 3L));
            long elapsed = System.currentTimeMillis() - startTime;
            
            // 3个缺口，2次间隔，每次50ms，至少100ms
            assertTrue(elapsed >= 100, "批量回补应遵守限流间隔");
        }

        @Test
        @DisplayName("自动回补按批量大小限制")
        void autoFill_respectsBatchSizeConfig() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(2);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            
            DataGap gap1 = createDataGap(1L, "1h", baseTime, baseTime.plus(1, ChronoUnit.HOURS), 1, "PENDING");
            DataGap gap2 = createDataGap(2L, "1h", baseTime.plus(2, ChronoUnit.HOURS), baseTime.plus(3, ChronoUnit.HOURS), 1, "PENDING");
            when(dataGapMapper.selectPendingWithLimit(2)).thenReturn(List.of(gap1, gap2));
            
            SyncStatus syncStatus = SyncStatus.builder().symbolId(1L).interval("1h").autoGapFillEnabled(false).build();
            when(syncService.getSyncStatus(anyLong(), anyString())).thenReturn(syncStatus);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(2, result.getTotalCount());
            verify(dataGapMapper).selectPendingWithLimit(2);
        }
    }

    // ==================== 26.5 验证周期级自动回补开关 ====================

    @Nested
    @DisplayName("26.5 验证周期级自动回补开关")
    class IntervalAutoFillSwitchTests {

        @Test
        @DisplayName("周期级开关关闭时跳过该周期缺口")
        void intervalSwitchOff_skipsGap() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(testGap));
            
            SyncStatus syncStatus = SyncStatus.builder()
                    .symbolId(1L).interval("1h").autoGapFillEnabled(false).build();
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(syncStatus);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getSkippedCount());
            assertEquals(0, result.getSuccessCount());
        }

        @Test
        @DisplayName("周期级开关开启时正常回补")
        void intervalSwitchOn_fillsGap() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(testGap));
            
            SyncStatus syncStatus = SyncStatus.builder()
                    .symbolId(1L).interval("1h").autoGapFillEnabled(true).build();
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(syncStatus);
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(anyLong())).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(createBinanceKline(baseTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(baseTime);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getSuccessCount());
            assertEquals(0, result.getSkippedCount());
        }
    }

    // ==================== 26.6 验证全局+周期级开关联动 ====================

    @Nested
    @DisplayName("26.6 验证全局+周期级开关联动")
    class GlobalAndIntervalSwitchTests {

        @Test
        @DisplayName("全局开关关闭时不检查周期级开关")
        void globalOff_ignoresIntervalSwitch() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertTrue(result.isDisabled());
            assertEquals("全局自动回补开关已关闭", result.getMessage());
            verify(syncService, never()).getSyncStatus(anyLong(), anyString());
        }

        @Test
        @DisplayName("全局开启+周期关闭时跳过")
        void globalOn_intervalOff_skips() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(testGap));
            
            SyncStatus syncStatus = SyncStatus.builder()
                    .symbolId(1L).interval("1h").autoGapFillEnabled(false).build();
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(syncStatus);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getSkippedCount());
            assertTrue(result.getSkippedGaps().get(0).getReason().contains("周期级"));
        }

        @Test
        @DisplayName("全局开启+周期开启时正常回补")
        void globalOn_intervalOn_fills() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(10L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(testGap));
            
            SyncStatus syncStatus = SyncStatus.builder()
                    .symbolId(1L).interval("1h").autoGapFillEnabled(true).build();
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(syncStatus);
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(anyLong())).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(createBinanceKline(baseTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(anyLong(), anyString())).thenReturn(baseTime);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getSuccessCount());
        }
    }

    // ==================== 26.7 验证删除后自动回补关闭 ====================

    @Nested
    @DisplayName("26.7 验证删除后自动回补关闭")
    class DeleteDisablesAutoFillTests {

        @Test
        @DisplayName("删除K线数据后关闭自动回补开关")
        void deleteKlines_disablesAutoGapFill() {
            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS))).thenReturn(10);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS))).thenReturn(1);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h")).thenReturn(baseTime);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h")).thenReturn(100L);
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", baseTime)).thenReturn(1);
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 100L)).thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false)).thenReturn(1);

            KlineService klineServiceReal = new KlineService(klineMapper, dataGapMapper, 
                    syncStatusMapper, symbolService);
            int deleted = klineServiceReal.deleteByTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS));

            assertEquals(10, deleted);
            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
        }

        @Test
        @DisplayName("删除整个周期K线数据后关闭自动回补开关")
        void deleteAllKlinesForInterval_disablesAutoGapFill() {
            when(klineMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(100);
            when(dataGapMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(5);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h")).thenReturn(null);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h")).thenReturn(0L);
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", null)).thenReturn(1);
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 0L)).thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false)).thenReturn(1);

            KlineService klineServiceReal = new KlineService(klineMapper, dataGapMapper, 
                    syncStatusMapper, symbolService);
            int deleted = klineServiceReal.deleteBySymbolIdAndInterval(1L, "1h");

            assertEquals(100, deleted);
            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
        }

        @Test
        @DisplayName("无数据删除时不关闭自动回补开关")
        void noDataDeleted_doesNotDisableAutoGapFill() {
            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS))).thenReturn(0);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS))).thenReturn(0);

            KlineService klineServiceReal = new KlineService(klineMapper, dataGapMapper, 
                    syncStatusMapper, symbolService);
            int deleted = klineServiceReal.deleteByTimeRange(1L, "1h", baseTime, 
                    baseTime.plus(1, ChronoUnit.DAYS));

            assertEquals(0, deleted);
            verify(syncStatusMapper, never()).updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean());
        }
    }

    // ==================== 26.8 验证缺口回补写入 sync_task ====================

    @Nested
    @DisplayName("26.8 验证缺口回补写入 sync_task")
    class GapFillCreatesSyncTaskTests {

        @Test
        @DisplayName("缺口回补创建 GAP_FILL 类型任务")
        void fillGap_createsSyncTask() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(1L, "1h", baseTime, baseTime.plus(2, ChronoUnit.HOURS)))
                    .thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(createBinanceKline(baseTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(1);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(baseTime);
            
            gapFillService.fillGap(1L);
            
            verify(syncService).createGapFillTask(1L, "1h", baseTime, baseTime.plus(2, ChronoUnit.HOURS));
            verify(syncService).startTask(1L);
        }

        @Test
        @DisplayName("回补成功后完成任务")
        void fillGap_success_completesTask() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> klines = List.of(
                    createBinanceKline(baseTime.toEpochMilli()),
                    createBinanceKline(baseTime.plus(1, ChronoUnit.HOURS).toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(2);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(baseTime.plus(1, ChronoUnit.HOURS));
            
            gapFillService.fillGap(1L);
            
            verify(syncService).completeTask(1L, 2);
        }

        @Test
        @DisplayName("回补失败后标记任务失败")
        void fillGap_failure_failsTask() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.error(-1, "API Error"));
            when(systemConfigService.getGapFillMaxRetry()).thenReturn(3);
            
            gapFillService.fillGap(1L);
            
            verify(syncService).failTask(eq(1L), contains("API Error"));
        }
    }

    // ==================== 26.9 验证缺口回补更新 sync_status ====================

    @Nested
    @DisplayName("26.9 验证缺口回补更新 sync_status")
    class GapFillUpdatesSyncStatusTests {

        @Test
        @DisplayName("回补成功后更新 sync_status")
        void fillGap_success_updatesSyncStatus() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            Instant lastKlineTime = baseTime.plus(1, ChronoUnit.HOURS);
            List<BinanceKline> klines = List.of(
                    createBinanceKline(baseTime.toEpochMilli()),
                    createBinanceKline(lastKlineTime.toEpochMilli()));
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(2);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(lastKlineTime);
            
            gapFillService.fillGap(1L);
            
            verify(syncService).updateSyncStatus(1L, "1h", lastKlineTime, 2);
        }

        @Test
        @DisplayName("回补0条数据时不更新 sync_status")
        void fillGap_noData_doesNotUpdateSyncStatus() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(Collections.emptyList()));
            
            gapFillService.fillGap(1L);
            
            verify(syncService, never()).updateSyncStatus(anyLong(), anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("回补失败时不更新 sync_status")
        void fillGap_failure_doesNotUpdateSyncStatus() {
            when(dataGapServiceMock.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.error(-1, "API Error"));
            when(systemConfigService.getGapFillMaxRetry()).thenReturn(3);
            
            gapFillService.fillGap(1L);
            
            verify(syncService, never()).updateSyncStatus(anyLong(), anyString(), any(), anyLong());
        }
    }
}

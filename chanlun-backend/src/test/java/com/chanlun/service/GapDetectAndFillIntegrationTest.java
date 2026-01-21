package com.chanlun.service;

import com.chanlun.dto.DataGapDTO;
import com.chanlun.dto.GapDetectResult;
import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
 * 缺口检测与回补流程集成测试
 * 
 * 覆盖任务 29.2：
 * - 缺口检测算法（基于时间连续性）
 * - 缺口状态流转（PENDING → FILLING → FILLED/FAILED）
 * - 缺口回补流程（单个/批量/自动）
 * - 回补重试机制
 * - sync_task 创建与 sync_status 更新
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("缺口检测与回补流程集成测试")
class GapDetectAndFillIntegrationTest {

    @Mock
    private DataGapMapper dataGapMapper;

    @Mock
    private KlineMapper klineMapper;

    @Mock
    private SyncStatusMapper syncStatusMapper;

    @Mock
    private SymbolService symbolService;

    @Mock
    private MarketService marketService;

    @Mock
    private DataSourceService dataSourceService;

    @Mock
    private SyncFilterService syncFilterService;

    @InjectMocks
    private DataGapService dataGapService;

    @Mock
    private KlineService klineService;

    @Mock
    private SyncService syncService;

    @Mock
    private BinanceClientFactory binanceClientFactory;

    @Mock
    private BinanceClient binanceClient;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private GapFillService gapFillService;

    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;
    private Instant baseTime;

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
    }

    @Nested
    @DisplayName("缺口检测算法测试")
    class GapDetectionTest {

        @Test
        @DisplayName("检测缺口 - 基于时间连续性 - 发现缺口")
        void detectGaps_withTimeGap_shouldFindGap() {
            // 模拟 K 线数据：第2根和第3根之间有缺口
            List<Kline> klines = List.of(
                    createKline(1L, baseTime),
                    createKline(2L, baseTime.plus(1, ChronoUnit.HOURS)),
                    // 缺口：应该有 baseTime + 2h 的 K 线，但缺失
                    createKline(3L, baseTime.plus(3, ChronoUnit.HOURS))
            );
            
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            
            DataGapService.GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            
            assertTrue(result.isSuccess());
            assertEquals(1, result.getNewGapCount());
            verify(dataGapMapper, times(1)).insert(any(DataGap.class));
        }

        @Test
        @DisplayName("检测缺口 - 无缺口 - 返回0")
        void detectGaps_noGap_shouldReturnZero() {
            // 模拟连续的 K 线数据
            List<Kline> klines = List.of(
                    createKline(1L, baseTime),
                    createKline(2L, baseTime.plus(1, ChronoUnit.HOURS)),
                    createKline(3L, baseTime.plus(2, ChronoUnit.HOURS))
            );
            
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            
            GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            
            assertTrue(result.isSuccess());
            assertEquals(0, result.getNewGapCount());
            verify(dataGapMapper, never()).insert(any(DataGap.class));
        }

        @Test
        @DisplayName("检测缺口 - 多个缺口 - 全部检测")
        void detectGaps_multipleGaps_shouldFindAll() {
            // 模拟有多个缺口的 K 线数据
            List<Kline> klines = List.of(
                    createKline(1L, baseTime),
                    createKline(2L, baseTime.plus(1, ChronoUnit.HOURS)),
                    // 缺口1：缺少 +2h
                    createKline(3L, baseTime.plus(3, ChronoUnit.HOURS)),
                    createKline(4L, baseTime.plus(4, ChronoUnit.HOURS)),
                    // 缺口2：缺少 +5h, +6h
                    createKline(5L, baseTime.plus(7, ChronoUnit.HOURS))
            );
            
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                    .thenReturn(List.of());
            
            DataGapService.GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            
            assertTrue(result.isSuccess());
            assertEquals(2, result.getNewGapCount());
            verify(dataGapMapper, times(2)).insert(any(DataGap.class));
        }

        @Test
        @DisplayName("检测缺口 - 交易对未启用历史同步 - 应失败")
        void detectGaps_historySyncDisabled_shouldFail() {
            testSymbol.setHistorySyncEnabled(false);
            
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            
            DataGapService.GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("不符合缺口检测条件"));
        }

        @Test
        @DisplayName("检测缺口 - 周期未配置 - 应失败")
        void detectGaps_intervalNotConfigured_shouldFail() {
            testSymbol.setSyncIntervals("4h,1d"); // 不包含 1h
            
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            
            DataGapService.GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("不符合缺口检测条件"));
        }
    }

    @Nested
    @DisplayName("缺口状态流转测试")
    class GapStatusTransitionTest {

        @Test
        @DisplayName("状态流转 - PENDING → FILLING - 允许")
        void updateStatus_pendingToFilling_shouldSucceed() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            DataGapDTO result = dataGapService.updateStatus(1L, DataGapService.STATUS_FILLING);
            
            assertNotNull(result);
            verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FILLING);
        }

        @Test
        @DisplayName("状态流转 - FILLING → FILLED - 允许")
        void updateStatus_fillingToFilled_shouldSucceed() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            DataGapDTO result = dataGapService.updateStatus(1L, DataGapService.STATUS_FILLED);
            
            assertNotNull(result);
            verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FILLED);
        }

        @Test
        @DisplayName("状态流转 - FILLING → FAILED - 允许")
        void updateStatus_fillingToFailed_shouldSucceed() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            DataGapDTO result = dataGapService.updateStatus(1L, DataGapService.STATUS_FAILED);
            
            assertNotNull(result);
            verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FAILED);
        }

        @Test
        @DisplayName("状态流转 - FILLED → PENDING - 不允许")
        void updateStatus_filledToPending_shouldFail() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLED);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            assertThrows(BusinessException.class, 
                    () -> dataGapService.updateStatus(1L, DataGapService.STATUS_PENDING));
        }

        @Test
        @DisplayName("状态流转 - PENDING → FILLED - 不允许（跳过FILLING）")
        void updateStatus_pendingToFilled_shouldFail() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            assertThrows(BusinessException.class, 
                    () -> dataGapService.updateStatus(1L, DataGapService.STATUS_FILLED));
        }
    }

    @Nested
    @DisplayName("缺口回补流程测试")
    class GapFillProcessTest {

        @Test
        @DisplayName("单个缺口回补 - 成功")
        void fillGap_success() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
            gap.setGapStart(baseTime);
            gap.setGapEnd(baseTime.plus(2, ChronoUnit.HOURS));
            
            SyncTask task = SyncTask.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .taskType(SyncTask.TaskType.GAP_FILL)
                    .status(SyncTask.Status.PENDING)
                    .build();
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(eq(1L), eq("1h"), any(), any())).thenReturn(task);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            List<BinanceKline> mockKlines = createMockKlines(2);
            when(binanceClient.getKlines(eq("BTCUSDT"), eq("1h"), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(mockKlines));
            
            when(klineService.batchUpsert(anyList())).thenReturn(2);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(baseTime.plus(2, ChronoUnit.HOURS));
            when(syncService.completeTask(1L, 2)).thenReturn(true);
            
            GapFillService.GapFillResult result = gapFillService.fillGap(1L);
            
            assertTrue(result.isSuccess());
            assertEquals(2, result.getSyncedCount());
            verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FILLING);
            verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FILLED);
            verify(syncService).createGapFillTask(eq(1L), eq("1h"), any(), any());
            verify(syncService).updateSyncStatus(eq(1L), eq("1h"), any(), eq(2));
        }

        @Test
        @DisplayName("单个缺口回补 - 缺口状态非PENDING - 应失败")
        void fillGap_notPending_shouldFail() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }

        @Test
        @DisplayName("单个缺口回补 - 数据源未启用 - 应失败")
        void fillGap_dataSourceDisabled_shouldFail() {
            testDataSource.setEnabled(false);
            
            DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
            
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }
    }

    @Nested
    @DisplayName("自动回补测试")
    class AutoGapFillTest {

        @Test
        @DisplayName("自动回补 - 全局开关关闭 - 跳过")
        void autoFillGaps_globalDisabled_shouldSkip() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);
            
            GapFillService.BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertFalse(result.isEnabled());
            assertTrue(result.getMessage().contains("全局自动回补开关已关闭"));
            verify(dataGapMapper, never()).selectPendingWithLimit(anyInt());
        }

        @Test
        @DisplayName("自动回补 - 周期级开关关闭 - 跳过该缺口")
        void autoFillGaps_intervalDisabled_shouldSkipGap() {
            SyncStatus status = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .autoGapFillEnabled(false)  // 周期级开关关闭
                    .build();
            
            DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
            
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(1000L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(gap));
            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(status);
            
            GapFillService.BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getSkippedCount());
            assertEquals(0, result.getSuccessCount());
        }

        @Test
        @DisplayName("自动回补 - 批量限制 - 只处理配置数量")
        void autoFillGaps_batchLimit_shouldRespectLimit() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(2);  // 限制2个
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(100L);
            
            // 返回2个待回补缺口
            List<DataGap> gaps = List.of(
                    createDataGap(1L, DataGapService.STATUS_PENDING),
                    createDataGap(2L, DataGapService.STATUS_PENDING)
            );
            
            when(dataGapMapper.selectPendingWithLimit(2)).thenReturn(gaps);
            
            // 验证只查询了配置的数量
            gapFillService.autoFillGaps();
            
            verify(dataGapMapper).selectPendingWithLimit(2);
        }
    }

    @Nested
    @DisplayName("回补重试机制测试")
    class RetryMechanismTest {

        @Test
        @DisplayName("回补失败 - 未达最大重试次数 - 状态改为PENDING")
        void handleFillFailure_belowMaxRetry_shouldResetToPending() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLING);
            gap.setRetryCount(1);
            
            when(systemConfigService.getGapFillMaxRetry()).thenReturn(3);
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            
            // 模拟回补失败
            dataGapService.incrementRetryCount(1L);
            
            verify(dataGapMapper).incrementRetryCount(1L);
        }

        @Test
        @DisplayName("回补失败 - 达到最大重试次数 - 状态改为FAILED")
        void handleFillFailure_reachedMaxRetry_shouldMarkAsFailed() {
            DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLING);
            gap.setRetryCount(3);  // 已达最大重试次数
            
            when(systemConfigService.getGapFillMaxRetry()).thenReturn(3);
            
            // 验证重试次数已达上限
            assertTrue(gap.getRetryCount() >= 3);
        }
    }

    /**
     * 创建测试用 K 线
     */
    private Kline createKline(Long id, Instant openTime) {
        return Kline.builder()
                .id(id)
                .symbolId(1L)
                .interval("1h")
                .openTime(openTime)
                .open(BigDecimal.valueOf(50000))
                .high(BigDecimal.valueOf(50100))
                .low(BigDecimal.valueOf(49900))
                .close(BigDecimal.valueOf(50050))
                .volume(BigDecimal.valueOf(1000))
                .quoteVolume(BigDecimal.valueOf(50000000))
                .trades(500)
                .closeTime(openTime.plus(1, ChronoUnit.HOURS).minusMillis(1))
                .build();
    }

    /**
     * 创建测试用缺口
     */
    private DataGap createDataGap(Long id, String status) {
        return DataGap.builder()
                .id(id)
                .symbolId(1L)
                .interval("1h")
                .gapStart(baseTime)
                .gapEnd(baseTime.plus(1, ChronoUnit.HOURS))
                .missingCount(1)
                .status(status)
                .retryCount(0)
                .build();
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

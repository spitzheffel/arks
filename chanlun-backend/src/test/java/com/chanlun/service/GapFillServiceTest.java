package com.chanlun.service;

import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.service.GapFillService.BatchGapFillResult;
import com.chanlun.service.GapFillService.GapFillResult;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GapFillService 测试")
class GapFillServiceTest {

    @Mock
    private DataGapMapper dataGapMapper;
    @Mock
    private DataGapService dataGapService;
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
    private GapFillService gapFillService;

    private Instant baseTime;
    private DataGap testGap;
    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;
    private SyncTask testTask;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        
        testGap = DataGap.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .gapStart(baseTime)
                .gapEnd(baseTime.plus(2, ChronoUnit.HOURS))
                .missingCount(2)
                .status("PENDING")
                .retryCount(0)
                .build();
        
        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .historySyncEnabled(true)
                .syncIntervals("1h,1d")
                .build();
        
        testMarket = Market.builder()
                .id(1L)
                .dataSourceId(1L)
                .name("Spot")
                .enabled(true)
                .build();
        
        testDataSource = DataSource.builder()
                .id(1L)
                .name("Binance")
                .exchangeType("BINANCE")
                .enabled(true)
                .deleted(false)
                .build();
        
        testTask = SyncTask.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .taskType("GAP_FILL")
                .status("PENDING")
                .build();
    }

    private BinanceKline createBinanceKline(long openTime) {
        return BinanceKline.builder()
                .openTime(openTime)
                .open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00"))
                .low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00"))
                .volume(new BigDecimal("1000.00"))
                .closeTime(openTime + 3600000 - 1)
                .quoteVolume(new BigDecimal("50000000.00"))
                .trades(100)
                .build();
    }

    @Nested
    @DisplayName("fillGap - 单个缺口回补测试")
    class FillGapTest {

        @Test
        @DisplayName("成功回补缺口")
        void fillGap_success() {
            // 准备数据
            when(dataGapService.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            when(syncService.createGapFillTask(anyLong(), anyString(), any(), any())).thenReturn(testTask);
            when(syncService.startTask(1L)).thenReturn(true);
            when(binanceClientFactory.createClient(testDataSource)).thenReturn(binanceClient);
            
            // 模拟 API 返回 K 线数据
            List<BinanceKline> klines = List.of(
                    createBinanceKline(baseTime.toEpochMilli()),
                    createBinanceKline(baseTime.plus(1, ChronoUnit.HOURS).toEpochMilli())
            );
            when(binanceClient.getKlines(anyString(), anyString(), any(Instant.class), any(Instant.class), anyInt()))
                    .thenReturn(BinanceApiResponse.success(klines));
            when(klineService.batchUpsert(anyList())).thenReturn(2);
            when(klineService.getMaxOpenTime(1L, "1h")).thenReturn(baseTime.plus(1, ChronoUnit.HOURS));
            
            // 执行
            GapFillResult result = gapFillService.fillGap(1L);
            
            // 验证
            assertTrue(result.isSuccess());
            assertEquals(1L, result.getGapId());
            assertEquals(2, result.getSyncedCount());
            
            // 验证状态更新
            verify(dataGapService).updateStatus(1L, "FILLING");
            verify(dataGapService).updateStatus(1L, "FILLED");
            verify(syncService).completeTask(1L, 2);
            verify(syncService).updateSyncStatus(eq(1L), eq("1h"), any(), eq(2L));
        }

        @Test
        @DisplayName("缺口已回补时抛出异常")
        void fillGap_alreadyFilled_throws() {
            testGap.setStatus("FILLED");
            when(dataGapService.findById(1L)).thenReturn(testGap);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }

        @Test
        @DisplayName("缺口正在回补中时抛出异常")
        void fillGap_filling_throws() {
            testGap.setStatus("FILLING");
            when(dataGapService.findById(1L)).thenReturn(testGap);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }

        @Test
        @DisplayName("数据源未启用时抛出异常")
        void fillGap_dataSourceDisabled_throws() {
            testDataSource.setEnabled(false);
            when(dataGapService.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }

        @Test
        @DisplayName("市场未启用时抛出异常")
        void fillGap_marketDisabled_throws() {
            testMarket.setEnabled(false);
            when(dataGapService.findById(1L)).thenReturn(testGap);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            
            assertThrows(BusinessException.class, () -> gapFillService.fillGap(1L));
        }
    }

    @Nested
    @DisplayName("batchFillGaps - 批量回补测试")
    class BatchFillGapsTest {

        @Test
        @DisplayName("空列表返回空结果")
        void batchFillGaps_emptyList_returnsEmpty() {
            BatchGapFillResult result = gapFillService.batchFillGaps(Collections.emptyList());
            
            assertEquals(0, result.getTotalCount());
            assertEquals(0, result.getSuccessCount());
        }

        @Test
        @DisplayName("null列表返回空结果")
        void batchFillGaps_nullList_returnsEmpty() {
            BatchGapFillResult result = gapFillService.batchFillGaps(null);
            
            assertEquals(0, result.getTotalCount());
        }
    }

    @Nested
    @DisplayName("autoFillGaps - 自动回补测试")
    class AutoFillGapsTest {

        @Test
        @DisplayName("全局开关关闭时返回禁用结果")
        void autoFillGaps_globalDisabled_returnsDisabled() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertTrue(result.isDisabled());
            assertEquals("全局自动回补开关已关闭", result.getMessage());
        }

        @Test
        @DisplayName("无待回补缺口时返回空结果")
        void autoFillGaps_noPendingGaps_returnsEmpty() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(Collections.emptyList());
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(0, result.getTotalCount());
            assertEquals("没有待回补的缺口", result.getMessage());
        }

        @Test
        @DisplayName("周期级开关关闭时跳过缺口")
        void autoFillGaps_intervalDisabled_skipsGap() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(systemConfigService.getGapFillBatchSize()).thenReturn(10);
            when(systemConfigService.getGapFillIntervalMs()).thenReturn(100L);
            when(dataGapMapper.selectPendingWithLimit(10)).thenReturn(List.of(testGap));
            
            // 周期级开关关闭
            SyncStatus syncStatus = SyncStatus.builder()
                    .symbolId(1L)
                    .interval("1h")
                    .autoGapFillEnabled(false)
                    .build();
            when(syncService.getSyncStatus(1L, "1h")).thenReturn(syncStatus);
            
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            assertEquals(1, result.getTotalCount());
            assertEquals(0, result.getSuccessCount());
            assertEquals(1, result.getSkippedCount());
        }
    }

    @Nested
    @DisplayName("resetFailedGap - 重置失败缺口测试")
    class ResetFailedGapTest {

        @Test
        @DisplayName("成功重置失败状态的缺口")
        void resetFailedGap_success() {
            testGap.setStatus("FAILED");
            when(dataGapService.findById(1L)).thenReturn(testGap);
            when(dataGapMapper.updateStatus(1L, "PENDING")).thenReturn(1);
            when(dataGapService.getById(1L)).thenReturn(
                    com.chanlun.dto.DataGapDTO.builder().id(1L).status("PENDING").build());
            
            var result = gapFillService.resetFailedGap(1L);
            
            assertEquals("PENDING", result.getStatus());
            verify(dataGapMapper).updateStatus(1L, "PENDING");
        }

        @Test
        @DisplayName("非失败状态的缺口无法重置")
        void resetFailedGap_notFailed_throws() {
            testGap.setStatus("PENDING");
            when(dataGapService.findById(1L)).thenReturn(testGap);
            
            assertThrows(BusinessException.class, () -> gapFillService.resetFailedGap(1L));
        }
    }
}

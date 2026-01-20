package com.chanlun.service;

import com.chanlun.dto.DataGapDTO;
import com.chanlun.dto.GapDetectResult;
import com.chanlun.entity.DataGap;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Kline;
import com.chanlun.entity.Market;
import com.chanlun.entity.Symbol;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataGapService 测试")
class DataGapServiceTest {

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

    private Instant baseTime;
    private Symbol testSymbol;
    private Market testMarket;
    private DataSource testDataSource;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        testSymbol = Symbol.builder()
                .id(1L).marketId(1L).symbol("BTCUSDT")
                .historySyncEnabled(true).syncIntervals("1m,1h,1d").build();
        testMarket = Market.builder()
                .id(1L).dataSourceId(1L).name("Spot").enabled(true).build();
        testDataSource = DataSource.builder()
                .id(1L).name("Binance").enabled(true).build();
    }

    private Kline createKline(Long symbolId, String interval, Instant openTime) {
        return Kline.builder().id(1L).symbolId(symbolId).interval(interval)
                .openTime(openTime).open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00")).low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00")).volume(new BigDecimal("1000.00")).build();
    }

    private DataGap createDataGap(Long symbolId, String interval, Instant gapStart, Instant gapEnd, int missingCount) {
        return DataGap.builder().id(1L).symbolId(symbolId).interval(interval)
                .gapStart(gapStart).gapEnd(gapEnd).missingCount(missingCount)
                .status("PENDING").retryCount(0).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Nested
    @DisplayName("getIntervalMillis tests")
    class GetIntervalMillisTest {
        @Test
        void interval_1m_returns_60000() {
            assertEquals(60 * 1000L, DataGapService.getIntervalMillis("1m"));
        }
        @Test
        void interval_1h_returns_3600000() {
            assertEquals(60 * 60 * 1000L, DataGapService.getIntervalMillis("1h"));
        }
        @Test
        void interval_1d_returns_86400000() {
            assertEquals(24 * 60 * 60 * 1000L, DataGapService.getIntervalMillis("1d"));
        }
        @Test
        void invalidInterval_throws() {
            assertThrows(BusinessException.class, () -> DataGapService.getIntervalMillis("1s"));
        }
    }

    @Nested
    @DisplayName("detectGaps tests")
    class DetectGapsTest {
        @Test
        void nullSymbolId_throws() {
            assertThrows(BusinessException.class, () -> dataGapService.detectGaps(null, "1h"));
        }
        @Test
        void nullInterval_throws() {
            assertThrows(BusinessException.class, () -> dataGapService.detectGaps(1L, null));
        }
        @Test
        void historySyncDisabled_returnsFail() {
            testSymbol.setHistorySyncEnabled(false);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            assertFalse(result.isSuccess());
        }
        @Test
        void continuousKlines_noGaps() {
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
        void singleGap_detected() {
            List<Kline> klines = List.of(
                    createKline(1L, "1h", baseTime),
                    createKline(1L, "1h", baseTime.plus(3, ChronoUnit.HOURS)));
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any())).thenReturn(Collections.emptyList());
            when(dataGapMapper.batchInsert(anyList())).thenReturn(1);
            when(dataGapMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(Collections.emptyList());
            GapDetectResult result = dataGapService.detectGaps(1L, "1h");
            assertTrue(result.isSuccess());
            assertEquals(1, result.getNewGapCount());
        }
    }

    @Nested
    @DisplayName("Status transition tests")
    class StatusTransitionTest {
        @Test
        void pendingToFilling_succeeds() {
            DataGap gap = createDataGap(1L, "1h", baseTime, baseTime.plus(1, ChronoUnit.HOURS), 1);
            gap.setStatus("PENDING");
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            when(dataGapMapper.updateStatus(1L, "FILLING")).thenReturn(1);
            when(symbolService.findById(1L)).thenReturn(testSymbol);
            when(marketService.findById(1L)).thenReturn(testMarket);
            when(dataSourceService.findById(1L)).thenReturn(testDataSource);
            DataGapDTO result = dataGapService.updateStatus(1L, "FILLING");
            assertEquals("FILLING", result.getStatus());
        }
        @Test
        void filledCannotChange() {
            DataGap gap = createDataGap(1L, "1h", baseTime, baseTime.plus(1, ChronoUnit.HOURS), 1);
            gap.setStatus("FILLED");
            when(dataGapMapper.selectById(1L)).thenReturn(gap);
            assertThrows(BusinessException.class, () -> dataGapService.updateStatus(1L, "PENDING"));
        }
    }

    @Nested
    @DisplayName("detectAllGaps tests - 批量缺口检测")
    class DetectAllGapsTest {

        private com.chanlun.dto.SymbolDTO createSymbolDTO(Long id, String symbol, List<String> intervals) {
            return com.chanlun.dto.SymbolDTO.builder()
                    .id(id)
                    .marketId(1L)
                    .marketName("Spot")
                    .dataSourceId(1L)
                    .dataSourceName("Binance")
                    .symbol(symbol)
                    .historySyncEnabled(true)
                    .syncIntervals(intervals)
                    .build();
        }

        @Test
        @DisplayName("无符合条件的交易对时返回空结果")
        void noEligibleSymbols_returnsEmptyResult() {
            when(syncFilterService.getGapDetectTargets()).thenReturn(Collections.emptyList());

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(0, result.getSymbolCount());
            assertEquals(0, result.getIntervalCount());
            assertEquals(0, result.getNewGapCount());
            assertEquals("没有符合条件的交易对", result.getMessage());
        }

        @Test
        @DisplayName("单个交易对单个周期检测成功")
        void singleSymbolSingleInterval_detectsGaps() {
            var symbolDTO = createSymbolDTO(1L, "BTCUSDT", List.of("1h"));
            when(syncFilterService.getGapDetectTargets()).thenReturn(List.of(symbolDTO));
            when(syncFilterService.getValidSyncIntervals(symbolDTO)).thenReturn(List.of("1h"));

            // 模拟有缺口的 K 线数据
            List<Kline> klines = List.of(
                    createKline(1L, "1h", baseTime),
                    createKline(1L, "1h", baseTime.plus(3, ChronoUnit.HOURS)));
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
            when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any())).thenReturn(Collections.emptyList());
            when(dataGapMapper.batchInsert(anyList())).thenReturn(1);
            when(dataGapMapper.countByStatus("PENDING")).thenReturn(1L);
            when(dataGapMapper.countByStatus("FILLING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FAILED")).thenReturn(0L);

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(1, result.getSymbolCount());
            assertEquals(1, result.getIntervalCount());
            assertEquals(1, result.getNewGapCount());
        }

        @Test
        @DisplayName("多个交易对多个周期检测成功")
        void multipleSymbolsMultipleIntervals_detectsGaps() {
            var symbol1 = createSymbolDTO(1L, "BTCUSDT", List.of("1h", "1d"));
            var symbol2 = createSymbolDTO(2L, "ETHUSDT", List.of("1h"));
            when(syncFilterService.getGapDetectTargets()).thenReturn(List.of(symbol1, symbol2));
            when(syncFilterService.getValidSyncIntervals(symbol1)).thenReturn(List.of("1h", "1d"));
            when(syncFilterService.getValidSyncIntervals(symbol2)).thenReturn(List.of("1h"));

            // 模拟连续的 K 线数据（无缺口）
            List<Kline> continuousKlines = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                continuousKlines.add(createKline(1L, "1h", baseTime.plus(i, ChronoUnit.HOURS)));
            }
            when(klineMapper.selectBySymbolIdAndInterval(anyLong(), anyString())).thenReturn(continuousKlines);
            when(dataGapMapper.countByStatus("PENDING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FILLING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FAILED")).thenReturn(0L);

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(2, result.getSymbolCount());
            assertEquals(3, result.getIntervalCount()); // 2 + 1 = 3 个周期
            assertEquals(0, result.getNewGapCount());
        }

        @Test
        @DisplayName("检测过程中异常不影响其他交易对")
        void exceptionInOneSymbol_continuesWithOthers() {
            var symbol1 = createSymbolDTO(1L, "BTCUSDT", List.of("1h"));
            var symbol2 = createSymbolDTO(2L, "ETHUSDT", List.of("1h"));
            when(syncFilterService.getGapDetectTargets()).thenReturn(List.of(symbol1, symbol2));
            when(syncFilterService.getValidSyncIntervals(symbol1)).thenReturn(List.of("1h"));
            when(syncFilterService.getValidSyncIntervals(symbol2)).thenReturn(List.of("1h"));

            // symbol1 抛出异常
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h"))
                    .thenThrow(new RuntimeException("Database error"));
            // symbol2 正常返回
            List<Kline> klines = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                klines.add(createKline(2L, "1h", baseTime.plus(i, ChronoUnit.HOURS)));
            }
            when(klineMapper.selectBySymbolIdAndInterval(2L, "1h")).thenReturn(klines);
            when(dataGapMapper.countByStatus("PENDING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FILLING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FAILED")).thenReturn(0L);

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(2, result.getSymbolCount());
            // symbol1 失败，symbol2 成功，所以只有 1 个周期成功处理
            assertEquals(1, result.getIntervalCount());
        }

        @Test
        @DisplayName("K线数据不足时不检测缺口")
        void insufficientKlines_noGapsDetected() {
            var symbolDTO = createSymbolDTO(1L, "BTCUSDT", List.of("1h"));
            when(syncFilterService.getGapDetectTargets()).thenReturn(List.of(symbolDTO));
            when(syncFilterService.getValidSyncIntervals(symbolDTO)).thenReturn(List.of("1h"));

            // 只有一根 K 线，无法检测缺口
            when(klineMapper.selectBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(List.of(createKline(1L, "1h", baseTime)));
            when(dataGapMapper.countByStatus("PENDING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FILLING")).thenReturn(0L);
            when(dataGapMapper.countByStatus("FAILED")).thenReturn(0L);

            GapDetectResult result = dataGapService.detectAllGaps();

            assertTrue(result.isSuccess());
            assertEquals(1, result.getSymbolCount());
            assertEquals(1, result.getIntervalCount());
            assertEquals(0, result.getNewGapCount());
        }
    }

    @Nested
    @DisplayName("Query tests")
    class QueryTest {
        @Test
        void getByIdNotExists_throws() {
            when(dataGapMapper.selectById(1L)).thenReturn(null);
            assertThrows(ResourceNotFoundException.class, () -> dataGapService.getById(1L));
        }
        @Test
        void countPending_returns() {
            when(dataGapMapper.countByStatus("PENDING")).thenReturn(5L);
            assertEquals(5L, dataGapService.countPending());
        }
    }
}

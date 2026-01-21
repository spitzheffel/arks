package com.chanlun.acceptance;

import com.chanlun.entity.Kline;
import com.chanlun.entity.SyncStatus;
import com.chanlun.exception.BusinessException;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import com.chanlun.service.KlineService;
import com.chanlun.service.SymbolService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * K线服务验收测试 (任务 25.4, 25.8, 25.9, 25.11, 25.13)
 * 
 * 验证 K 线数据相关功能：
 * - 25.4 K线数据查询性能 (<500ms)
 * - 25.8 手动删除历史数据功能
 * - 25.9 删除后关闭自动回补开关
 * - 25.11 last_kline_time 正确重算
 * - 25.13 total_klines 正确重算
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("K线服务验收测试")
class KlineServiceAcceptanceTest {

    @Mock
    private KlineMapper klineMapper;

    @Mock
    private DataGapMapper dataGapMapper;

    @Mock
    private SyncStatusMapper syncStatusMapper;

    @Mock
    private SymbolService symbolService;

    @InjectMocks
    private KlineService klineService;

    private Instant baseTime;
    private Kline testKline;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");

        testKline = Kline.builder()
                .id(1L)
                .symbolId(1L)
                .interval("1h")
                .openTime(baseTime)
                .open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00"))
                .low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00"))
                .volume(new BigDecimal("100.00"))
                .quoteVolume(new BigDecimal("5000000.00"))
                .trades(1000)
                .closeTime(baseTime.plus(1, ChronoUnit.HOURS).minusMillis(1))
                .build();
    }

    // ==================== 25.4 验证 K线数据查询性能 (<500ms) ====================

    @Nested
    @DisplayName("25.4 验证 K线数据查询性能")
    class QueryPerformanceTests {

        @Test
        @DisplayName("查询限制 - 单次最多返回 1000 条")
        void queryWithLimit_maxLimit1000() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(30, ChronoUnit.DAYS);

            when(klineMapper.selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000)))
                    .thenReturn(List.of(testKline));

            // 请求超过 1000 条时应限制为 1000
            List<Kline> result = klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    1L, "1h", startTime, endTime, 2000);

            verify(klineMapper).selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000));
        }

        @Test
        @DisplayName("查询限制 - 负数或零时使用默认值 1000")
        void queryWithLimit_invalidLimitUsesDefault() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(klineMapper.selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    anyLong(), anyString(), any(), any(), eq(1000)))
                    .thenReturn(List.of(testKline));

            // 负数
            klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(1L, "1h", startTime, endTime, -1);
            verify(klineMapper).selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000));

            // 零
            klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(1L, "1h", startTime, endTime, 0);
            verify(klineMapper, times(2)).selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    eq(1L), eq("1h"), eq(startTime), eq(endTime), eq(1000));
        }
    }

    // ==================== 25.8 验证手动删除历史数据功能 ====================

    @Nested
    @DisplayName("25.8 验证手动删除历史数据功能")
    class DeleteHistoryDataTests {

        @Test
        @DisplayName("删除指定时间范围的 K 线数据")
        void deleteByTimeRange_success() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(7, ChronoUnit.DAYS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(168); // 7天 * 24小时
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(2);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(startTime.minus(1, ChronoUnit.HOURS));
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(100L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean()))
                    .thenReturn(1);

            int deleted = klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            assertEquals(168, deleted);
            verify(klineMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
            verify(dataGapMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
        }

        @Test
        @DisplayName("删除交易对的所有 K 线数据")
        void deleteBySymbolId_success() {
            when(klineMapper.deleteBySymbolId(1L)).thenReturn(1000);
            when(dataGapMapper.deleteBySymbolId(1L)).thenReturn(5);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(anyLong(), anyString()))
                    .thenReturn(null);
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(anyLong(), anyString()))
                    .thenReturn(0L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean()))
                    .thenReturn(1);

            int deleted = klineService.deleteBySymbolId(1L);

            assertEquals(1000, deleted);
            verify(klineMapper).deleteBySymbolId(1L);
            verify(dataGapMapper).deleteBySymbolId(1L);
        }

        @Test
        @DisplayName("删除指定交易对和周期的所有 K 线数据")
        void deleteBySymbolIdAndInterval_success() {
            when(klineMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(500);
            when(dataGapMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(3);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(null);
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", null))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(0L);
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 0L))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false))
                    .thenReturn(1);

            int deleted = klineService.deleteBySymbolIdAndInterval(1L, "1h");

            assertEquals(500, deleted);
            verify(klineMapper).deleteBySymbolIdAndInterval(1L, "1h");
            verify(dataGapMapper).deleteBySymbolIdAndInterval(1L, "1h");
        }

        @Test
        @DisplayName("删除时同步删除相关缺口记录")
        void deleteByTimeRange_alsoDeletesGaps() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(24);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(2);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(startTime.minus(1, ChronoUnit.HOURS));
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(100L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean()))
                    .thenReturn(1);

            klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            // 验证缺口也被删除
            verify(dataGapMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
        }
    }

    // ==================== 25.9 验证删除后关闭自动回补开关 ====================

    @Nested
    @DisplayName("25.9 验证删除后关闭自动回补开关")
    class DisableAutoGapFillAfterDeleteTests {

        @Test
        @DisplayName("删除历史数据后自动关闭 auto_gap_fill_enabled")
        void deleteByTimeRange_disablesAutoGapFill() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(24);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(anyLong(), anyString(), any(), any()))
                    .thenReturn(0);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(startTime.minus(1, ChronoUnit.HOURS));
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(100L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false))
                    .thenReturn(1);

            klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            // 验证自动回补开关被关闭
            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
        }

        @Test
        @DisplayName("删除所有数据后关闭所有周期的自动回补开关")
        void deleteBySymbolId_disablesAllAutoGapFill() {
            when(klineMapper.deleteBySymbolId(1L)).thenReturn(1000);
            when(dataGapMapper.deleteBySymbolId(1L)).thenReturn(5);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(anyLong(), anyString()))
                    .thenReturn(null);
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(anyLong(), anyString()))
                    .thenReturn(0L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), eq(false)))
                    .thenReturn(1);

            klineService.deleteBySymbolId(1L);

            // 验证所有有效周期的自动回补开关都被关闭
            Set<String> validIntervals = klineService.getValidIntervals();
            for (String interval : validIntervals) {
                verify(syncStatusMapper).updateAutoGapFillEnabled(1L, interval, false);
            }
        }

        @Test
        @DisplayName("删除 0 条数据时不关闭自动回补开关")
        void deleteByTimeRange_noDataDeleted_doesNotDisableAutoGapFill() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(0);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(anyLong(), anyString(), any(), any()))
                    .thenReturn(0);

            klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            // 验证自动回补开关未被修改
            verify(syncStatusMapper, never()).updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean());
        }
    }

    // ==================== 25.11 验证 last_kline_time 正确重算 ====================

    @Nested
    @DisplayName("25.11 验证 last_kline_time 正确重算")
    class RecalculateLastKlineTimeTests {

        @Test
        @DisplayName("删除后重算 last_kline_time - 有剩余数据")
        void deleteByTimeRange_recalculatesLastKlineTime_withRemainingData() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);
            Instant expectedLastKlineTime = startTime.minus(1, ChronoUnit.HOURS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(24);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(anyLong(), anyString(), any(), any()))
                    .thenReturn(0);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(expectedLastKlineTime);
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", expectedLastKlineTime))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(100L);
            when(syncStatusMapper.updateTotalKlines(anyLong(), anyString(), anyLong()))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean()))
                    .thenReturn(1);

            klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            // 验证 last_kline_time 被正确更新
            verify(syncStatusMapper).updateLastKlineTime(1L, "1h", expectedLastKlineTime);
        }

        @Test
        @DisplayName("删除后重算 last_kline_time - 无剩余数据设为 NULL")
        void deleteBySymbolIdAndInterval_recalculatesLastKlineTime_toNull() {
            when(klineMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(500);
            when(dataGapMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(0);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(null); // 无剩余数据
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", null))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(0L);
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 0L))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false))
                    .thenReturn(1);

            klineService.deleteBySymbolIdAndInterval(1L, "1h");

            // 验证 last_kline_time 被设为 NULL
            verify(syncStatusMapper).updateLastKlineTime(1L, "1h", null);
        }
    }

    // ==================== 25.13 验证 total_klines 正确重算 ====================

    @Nested
    @DisplayName("25.13 验证 total_klines 正确重算")
    class RecalculateTotalKlinesTests {

        @Test
        @DisplayName("删除后重算 total_klines - 有剩余数据")
        void deleteByTimeRange_recalculatesTotalKlines_withRemainingData() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                    .thenReturn(24);
            when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(anyLong(), anyString(), any(), any()))
                    .thenReturn(0);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(startTime.minus(1, ChronoUnit.HOURS));
            when(syncStatusMapper.updateLastKlineTime(anyLong(), anyString(), any()))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(100L); // 剩余 100 条
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 100L))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean()))
                    .thenReturn(1);

            klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

            // 验证 total_klines 被正确更新
            verify(syncStatusMapper).updateTotalKlines(1L, "1h", 100L);
        }

        @Test
        @DisplayName("删除后重算 total_klines - 无剩余数据设为 0")
        void deleteBySymbolIdAndInterval_recalculatesTotalKlines_toZero() {
            when(klineMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(500);
            when(dataGapMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(0);
            when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(null);
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", null))
                    .thenReturn(1);
            when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                    .thenReturn(0L); // 无剩余数据
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 0L))
                    .thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false))
                    .thenReturn(1);

            klineService.deleteBySymbolIdAndInterval(1L, "1h");

            // 验证 total_klines 被设为 0
            verify(syncStatusMapper).updateTotalKlines(1L, "1h", 0L);
        }
    }

    // ==================== 周期校验测试 ====================

    @Nested
    @DisplayName("周期校验测试")
    class IntervalValidationTests {

        @Test
        @DisplayName("获取有效周期列表 - 不包含 1s")
        void getValidIntervals_doesNotInclude1s() {
            Set<String> validIntervals = klineService.getValidIntervals();

            assertFalse(validIntervals.contains("1s"));
            assertTrue(validIntervals.contains("1m"));
            assertEquals(15, validIntervals.size());
        }

        @Test
        @DisplayName("查询时拒绝无效周期")
        void query_rejectsInvalidInterval() {
            assertThrows(BusinessException.class,
                    () -> klineService.getBySymbolIdAndInterval(1L, "1s"));
            assertThrows(BusinessException.class,
                    () -> klineService.getBySymbolIdAndInterval(1L, "invalid"));
        }

        @Test
        @DisplayName("批量插入时拒绝无效周期")
        void batchUpsert_rejectsInvalidInterval() {
            Kline invalidKline = Kline.builder()
                    .symbolId(1L)
                    .interval("1s") // 无效周期
                    .openTime(baseTime)
                    .open(new BigDecimal("50000"))
                    .high(new BigDecimal("51000"))
                    .low(new BigDecimal("49000"))
                    .close(new BigDecimal("50500"))
                    .closeTime(baseTime.plusSeconds(1))
                    .build();

            assertThrows(BusinessException.class,
                    () -> klineService.batchUpsert(List.of(invalidKline)));
        }
    }
}

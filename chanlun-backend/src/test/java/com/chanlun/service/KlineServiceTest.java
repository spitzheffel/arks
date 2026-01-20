package com.chanlun.service;

import com.chanlun.entity.Kline;
import com.chanlun.exception.BusinessException;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * K线数据服务测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KlineService 测试")
class KlineServiceTest {

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

    private Kline testKline;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        testKline = createTestKline(1L, "1h", baseTime);
    }

    private Kline createTestKline(Long symbolId, String interval, Instant openTime) {
        Instant closeTime = openTime != null 
                ? openTime.plus(1, ChronoUnit.HOURS).minusMillis(1) 
                : null;
        return Kline.builder()
                .symbolId(symbolId)
                .interval(interval)
                .openTime(openTime)
                .open(new BigDecimal("50000.00"))
                .high(new BigDecimal("51000.00"))
                .low(new BigDecimal("49000.00"))
                .close(new BigDecimal("50500.00"))
                .volume(new BigDecimal("1000.00"))
                .quoteVolume(new BigDecimal("50000000.00"))
                .trades(10000)
                .closeTime(closeTime)
                .build();
    }

    // ==================== 批量插入/更新测试 ====================

    @Test
    @DisplayName("批量插入 - 空列表应返回0")
    void batchUpsert_emptyList_shouldReturnZero() {
        int result = klineService.batchUpsert(Collections.emptyList());
        assertEquals(0, result);
        verify(klineMapper, never()).batchUpsert(anyList());
    }

    @Test
    @DisplayName("批量插入 - null列表应返回0")
    void batchUpsert_nullList_shouldReturnZero() {
        int result = klineService.batchUpsert(null);
        assertEquals(0, result);
        verify(klineMapper, never()).batchUpsert(anyList());
    }

    @Test
    @DisplayName("批量插入 - 单条数据成功")
    void batchUpsert_singleKline_success() {
        List<Kline> klines = List.of(testKline);
        when(klineMapper.batchUpsert(anyList())).thenReturn(1);

        int result = klineService.batchUpsert(klines);

        assertEquals(1, result);
        verify(klineMapper).batchUpsert(klines);
    }

    @Test
    @DisplayName("批量插入 - 多条数据成功")
    void batchUpsert_multipleKlines_success() {
        List<Kline> klines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            klines.add(createTestKline(1L, "1h", baseTime.plus(i, ChronoUnit.HOURS)));
        }
        when(klineMapper.batchUpsert(anyList())).thenReturn(10);

        int result = klineService.batchUpsert(klines);

        assertEquals(10, result);
        verify(klineMapper).batchUpsert(klines);
    }


    @Test
    @DisplayName("批量插入 - 大批量数据应分批处理")
    void batchUpsert_largeBatch_shouldPartition() {
        // 创建 600 条数据，应分成 2 批（默认批大小 500）
        List<Kline> klines = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            klines.add(createTestKline(1L, "1m", baseTime.plus(i, ChronoUnit.MINUTES)));
        }
        when(klineMapper.batchUpsert(anyList())).thenReturn(500, 100);

        int result = klineService.batchUpsert(klines);

        assertEquals(600, result);
        verify(klineMapper, times(2)).batchUpsert(anyList());
    }

    @Test
    @DisplayName("批量插入 - 指定批大小")
    void batchUpsert_customBatchSize_success() {
        List<Kline> klines = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            klines.add(createTestKline(1L, "1m", baseTime.plus(i, ChronoUnit.MINUTES)));
        }
        when(klineMapper.batchUpsert(anyList())).thenReturn(50);

        int result = klineService.batchUpsert(klines, 50);

        assertEquals(100, result);
        verify(klineMapper, times(2)).batchUpsert(anyList());
    }

    // ==================== 数据校验测试 ====================

    @Test
    @DisplayName("批量插入 - symbolId为空应抛出异常")
    void batchUpsert_nullSymbolId_shouldThrowException() {
        Kline invalidKline = createTestKline(null, "1h", baseTime);
        List<Kline> klines = List.of(invalidKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    @Test
    @DisplayName("批量插入 - interval为空应抛出异常")
    void batchUpsert_nullInterval_shouldThrowException() {
        Kline invalidKline = createTestKline(1L, null, baseTime);
        List<Kline> klines = List.of(invalidKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    @Test
    @DisplayName("批量插入 - 不支持的周期应抛出异常")
    void batchUpsert_invalidInterval_shouldThrowException() {
        Kline invalidKline = createTestKline(1L, "1s", baseTime);
        List<Kline> klines = List.of(invalidKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    @Test
    @DisplayName("批量插入 - openTime为空应抛出异常")
    void batchUpsert_nullOpenTime_shouldThrowException() {
        Kline invalidKline = createTestKline(1L, "1h", null);
        List<Kline> klines = List.of(invalidKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    @Test
    @DisplayName("批量插入 - open为空应抛出异常")
    void batchUpsert_nullOpen_shouldThrowException() {
        testKline.setOpen(null);
        List<Kline> klines = List.of(testKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    @Test
    @DisplayName("批量插入 - closeTime为空应抛出异常")
    void batchUpsert_nullCloseTime_shouldThrowException() {
        testKline.setCloseTime(null);
        List<Kline> klines = List.of(testKline);

        assertThrows(BusinessException.class, () -> klineService.batchUpsert(klines));
    }

    // ==================== 单条插入测试 ====================

    @Test
    @DisplayName("单条插入 - 成功")
    void upsert_success() {
        when(klineMapper.batchUpsert(anyList())).thenReturn(1);

        boolean result = klineService.upsert(testKline);

        assertTrue(result);
        verify(klineMapper).batchUpsert(anyList());
    }

    @Test
    @DisplayName("单条插入 - null应返回false")
    void upsert_null_shouldReturnFalse() {
        boolean result = klineService.upsert(null);
        assertFalse(result);
        verify(klineMapper, never()).batchUpsert(anyList());
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("按交易对和周期查询 - 成功")
    void getBySymbolIdAndInterval_success() {
        List<Kline> expected = List.of(testKline);
        when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(expected);

        List<Kline> result = klineService.getBySymbolIdAndInterval(1L, "1h");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("按交易对和周期查询 - symbolId为空应抛出异常")
    void getBySymbolIdAndInterval_nullSymbolId_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndInterval(null, "1h"));
    }

    @Test
    @DisplayName("按交易对和周期查询 - interval为空应抛出异常")
    void getBySymbolIdAndInterval_nullInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndInterval(1L, null));
    }

    @Test
    @DisplayName("按交易对和周期查询 - 不支持的周期应抛出异常")
    void getBySymbolIdAndInterval_invalidInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndInterval(1L, "1s"));
    }


    // ==================== 时间范围查询测试 ====================

    @Test
    @DisplayName("按时间范围查询 - 成功")
    void getBySymbolIdAndIntervalAndTimeRange_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        List<Kline> expected = List.of(testKline);
        when(klineMapper.selectBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(expected);

        List<Kline> result = klineService.getBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("按时间范围查询 - startTime为空应抛出异常")
    void getBySymbolIdAndIntervalAndTimeRange_nullStartTime_shouldThrowException() {
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndIntervalAndTimeRange(1L, "1h", null, endTime));
    }

    @Test
    @DisplayName("按时间范围查询 - endTime为空应抛出异常")
    void getBySymbolIdAndIntervalAndTimeRange_nullEndTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndIntervalAndTimeRange(1L, "1h", baseTime, null));
    }

    @Test
    @DisplayName("按时间范围查询 - startTime晚于endTime应抛出异常")
    void getBySymbolIdAndIntervalAndTimeRange_invalidTimeRange_shouldThrowException() {
        Instant startTime = baseTime.plus(24, ChronoUnit.HOURS);
        Instant endTime = baseTime;
        assertThrows(BusinessException.class, 
                () -> klineService.getBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime));
    }

    @Test
    @DisplayName("按时间范围查询带限制 - 成功")
    void getBySymbolIdAndIntervalAndTimeRangeWithLimit_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        List<Kline> expected = List.of(testKline);
        when(klineMapper.selectBySymbolIdAndIntervalAndTimeRangeWithLimit(1L, "1h", startTime, endTime, 100))
                .thenReturn(expected);

        List<Kline> result = klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                1L, "1h", startTime, endTime, 100);

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("按时间范围查询带限制 - 超过1000应限制为1000")
    void getBySymbolIdAndIntervalAndTimeRangeWithLimit_exceedMax_shouldLimitTo1000() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        when(klineMapper.selectBySymbolIdAndIntervalAndTimeRangeWithLimit(eq(1L), eq("1h"), 
                eq(startTime), eq(endTime), eq(1000))).thenReturn(Collections.emptyList());

        klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(1L, "1h", startTime, endTime, 2000);

        verify(klineMapper).selectBySymbolIdAndIntervalAndTimeRangeWithLimit(1L, "1h", startTime, endTime, 1000);
    }

    // ==================== 最新/最早K线查询测试 ====================

    @Test
    @DisplayName("查询最新K线 - 成功")
    void getLatest_success() {
        when(klineMapper.selectLatestBySymbolIdAndInterval(1L, "1h")).thenReturn(testKline);

        Kline result = klineService.getLatest(1L, "1h");

        assertEquals(testKline, result);
    }

    @Test
    @DisplayName("查询最新K线 - 不存在返回null")
    void getLatest_notFound_shouldReturnNull() {
        when(klineMapper.selectLatestBySymbolIdAndInterval(1L, "1h")).thenReturn(null);

        Kline result = klineService.getLatest(1L, "1h");

        assertNull(result);
    }

    @Test
    @DisplayName("查询最早K线 - 成功")
    void getEarliest_success() {
        when(klineMapper.selectEarliestBySymbolIdAndInterval(1L, "1h")).thenReturn(testKline);

        Kline result = klineService.getEarliest(1L, "1h");

        assertEquals(testKline, result);
    }

    // ==================== 按开盘时间查询测试 ====================

    @Test
    @DisplayName("按开盘时间查询 - 成功")
    void getByOpenTime_success() {
        when(klineMapper.selectBySymbolIdAndIntervalAndOpenTime(1L, "1h", baseTime))
                .thenReturn(testKline);

        Kline result = klineService.getByOpenTime(1L, "1h", baseTime);

        assertEquals(testKline, result);
    }

    @Test
    @DisplayName("按开盘时间查询 - openTime为空应抛出异常")
    void getByOpenTime_nullOpenTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> klineService.getByOpenTime(1L, "1h", null));
    }

    // ==================== 统计测试 ====================

    @Test
    @DisplayName("统计K线数量 - 成功")
    void count_success() {
        when(klineMapper.countBySymbolIdAndInterval(1L, "1h")).thenReturn(100L);

        long result = klineService.count(1L, "1h");

        assertEquals(100L, result);
    }

    @Test
    @DisplayName("统计时间范围内K线数量 - 成功")
    void countInTimeRange_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        when(klineMapper.countBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(24L);

        long result = klineService.countInTimeRange(1L, "1h", startTime, endTime);

        assertEquals(24L, result);
    }


    // ==================== 最大/最小开盘时间测试 ====================

    @Test
    @DisplayName("获取最大开盘时间 - 成功")
    void getMaxOpenTime_success() {
        Instant expected = baseTime.plus(100, ChronoUnit.HOURS);
        when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h")).thenReturn(expected);

        Instant result = klineService.getMaxOpenTime(1L, "1h");

        assertEquals(expected, result);
    }

    @Test
    @DisplayName("获取最大开盘时间 - 无数据返回null")
    void getMaxOpenTime_noData_shouldReturnNull() {
        when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h")).thenReturn(null);

        Instant result = klineService.getMaxOpenTime(1L, "1h");

        assertNull(result);
    }

    @Test
    @DisplayName("获取最小开盘时间 - 成功")
    void getMinOpenTime_success() {
        when(klineMapper.selectMinOpenTimeBySymbolIdAndInterval(1L, "1h")).thenReturn(baseTime);

        Instant result = klineService.getMinOpenTime(1L, "1h");

        assertEquals(baseTime, result);
    }

    // ==================== 删除测试 ====================

    @Test
    @DisplayName("按时间范围删除 - 成功并同步删除缺口记录和重新计算last_kline_time和total_klines并关闭自动回补")
    void deleteByTimeRange_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        Instant newMaxOpenTime = baseTime.plus(48, ChronoUnit.HOURS);
        when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(24);
        when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(2);
        when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(1L, "1h"))
                .thenReturn(newMaxOpenTime);
        when(syncStatusMapper.updateLastKlineTime(1L, "1h", newMaxOpenTime))
                .thenReturn(1);
        when(klineMapper.countBySymbolIdAndInterval(1L, "1h"))
                .thenReturn(100L);
        when(syncStatusMapper.updateTotalKlines(1L, "1h", 100L))
                .thenReturn(1);
        when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false))
                .thenReturn(1);

        int result = klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

        assertEquals(24, result);
        verify(klineMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
        verify(dataGapMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
        verify(syncStatusMapper).updateLastKlineTime(1L, "1h", newMaxOpenTime);
        verify(syncStatusMapper).updateTotalKlines(1L, "1h", 100L);
        verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
    }

    @Test
    @DisplayName("按时间范围删除 - 删除所有数据后last_kline_time应设为null且total_klines为0并关闭自动回补")
    void deleteByTimeRange_allDeleted_shouldSetLastKlineTimeToNullAndTotalKlinesToZero() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(24);
        when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(0);
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

        int result = klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

        assertEquals(24, result);
        verify(syncStatusMapper).updateLastKlineTime(1L, "1h", null);
        verify(syncStatusMapper).updateTotalKlines(1L, "1h", 0L);
        verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
    }

    @Test
    @DisplayName("按时间范围删除 - 无K线但有缺口记录也应删除但不关闭自动回补")
    void deleteByTimeRange_noKlinesButHasGaps_shouldDeleteGaps() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        when(klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(0);
        when(dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime))
                .thenReturn(3);

        int result = klineService.deleteByTimeRange(1L, "1h", startTime, endTime);

        assertEquals(0, result);
        verify(dataGapMapper).deleteBySymbolIdAndIntervalAndTimeRange(1L, "1h", startTime, endTime);
        // 无K线删除时不应调用重新计算和关闭自动回补
        verify(syncStatusMapper, never()).updateLastKlineTime(anyLong(), anyString(), any());
        verify(syncStatusMapper, never()).updateTotalKlines(anyLong(), anyString(), anyLong());
        verify(syncStatusMapper, never()).updateAutoGapFillEnabled(anyLong(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("按交易对ID删除 - 成功并同步删除缺口记录和重新计算所有周期的last_kline_time和total_klines并关闭自动回补")
    void deleteBySymbolId_success() {
        when(klineMapper.deleteBySymbolId(1L)).thenReturn(1000);
        when(dataGapMapper.deleteBySymbolId(1L)).thenReturn(5);
        when(klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(eq(1L), anyString()))
                .thenReturn(null);
        when(syncStatusMapper.updateLastKlineTime(eq(1L), anyString(), isNull()))
                .thenReturn(1);
        when(klineMapper.countBySymbolIdAndInterval(eq(1L), anyString()))
                .thenReturn(0L);
        when(syncStatusMapper.updateTotalKlines(eq(1L), anyString(), eq(0L)))
                .thenReturn(1);
        when(syncStatusMapper.updateAutoGapFillEnabled(eq(1L), anyString(), eq(false)))
                .thenReturn(1);

        int result = klineService.deleteBySymbolId(1L);

        assertEquals(1000, result);
        verify(klineMapper).deleteBySymbolId(1L);
        verify(dataGapMapper).deleteBySymbolId(1L);
        // 应该为所有有效周期调用重新计算和关闭自动回补
        verify(syncStatusMapper, atLeast(1)).updateLastKlineTime(eq(1L), anyString(), any());
        verify(syncStatusMapper, atLeast(1)).updateTotalKlines(eq(1L), anyString(), anyLong());
        verify(syncStatusMapper, atLeast(1)).updateAutoGapFillEnabled(eq(1L), anyString(), eq(false));
    }

    @Test
    @DisplayName("按交易对ID和周期删除 - 成功并同步删除缺口记录和重新计算last_kline_time和total_klines并关闭自动回补")
    void deleteBySymbolIdAndInterval_success() {
        when(klineMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(100);
        when(dataGapMapper.deleteBySymbolIdAndInterval(1L, "1h")).thenReturn(2);
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

        int result = klineService.deleteBySymbolIdAndInterval(1L, "1h");

        assertEquals(100, result);
        verify(klineMapper).deleteBySymbolIdAndInterval(1L, "1h");
        verify(dataGapMapper).deleteBySymbolIdAndInterval(1L, "1h");
        verify(syncStatusMapper).updateLastKlineTime(1L, "1h", null);
        verify(syncStatusMapper).updateTotalKlines(1L, "1h", 0L);
        verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
    }

    // ==================== 存在性检查测试 ====================

    @Test
    @DisplayName("检查K线存在 - 存在返回true")
    void exists_found_shouldReturnTrue() {
        when(klineMapper.selectBySymbolIdAndIntervalAndOpenTime(1L, "1h", baseTime))
                .thenReturn(testKline);

        boolean result = klineService.exists(1L, "1h", baseTime);

        assertTrue(result);
    }

    @Test
    @DisplayName("检查K线存在 - 不存在返回false")
    void exists_notFound_shouldReturnFalse() {
        when(klineMapper.selectBySymbolIdAndIntervalAndOpenTime(1L, "1h", baseTime))
                .thenReturn(null);

        boolean result = klineService.exists(1L, "1h", baseTime);

        assertFalse(result);
    }

    // ==================== 有效周期测试 ====================

    @Test
    @DisplayName("获取有效周期列表 - 应包含所有支持的周期")
    void getValidIntervals_shouldContainAllSupportedIntervals() {
        var intervals = klineService.getValidIntervals();

        assertTrue(intervals.contains("1m"));
        assertTrue(intervals.contains("5m"));
        assertTrue(intervals.contains("15m"));
        assertTrue(intervals.contains("1h"));
        assertTrue(intervals.contains("4h"));
        assertTrue(intervals.contains("1d"));
        assertTrue(intervals.contains("1w"));
        assertTrue(intervals.contains("1M"));
        // 不应包含 1s
        assertFalse(intervals.contains("1s"));
    }
}

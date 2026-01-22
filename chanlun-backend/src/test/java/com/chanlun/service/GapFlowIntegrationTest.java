package com.chanlun.service;

import com.chanlun.dto.GapDetectResult;
import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 缺口检测与回补流程测试 (任务 29.2)
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("缺口检测与回补流程测试")
class GapFlowIntegrationTest {

    @Mock
    private DataGapMapper dataGapMapper;

    @Mock
    private KlineMapper klineMapper;

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

    private Symbol testSymbol;
    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
        
        testSymbol = Symbol.builder()
                .id(1L)
                .marketId(1L)
                .symbol("BTCUSDT")
                .historySyncEnabled(true)
                .syncIntervals("1h,4h,1d")
                .build();
    }

    @Test
    @DisplayName("缺口检测 - 发现时间缺口")
    void detectGaps_withTimeGap_shouldFindGap() {
        List<Kline> klines = List.of(
                createKline(1L, baseTime),
                createKline(2L, baseTime.plus(1, ChronoUnit.HOURS)),
                createKline(3L, baseTime.plus(3, ChronoUnit.HOURS))
        );
        
        when(symbolService.findById(1L)).thenReturn(testSymbol);
        when(klineMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(klines);
        when(dataGapMapper.selectOverlapping(anyLong(), anyString(), any(), any()))
                .thenReturn(List.of());
        
        GapDetectResult result = dataGapService.detectGaps(1L, "1h");
        
        assertTrue(result.isSuccess());
        assertEquals(1, result.getNewGapCount());
        verify(dataGapMapper, times(1)).insert(any(DataGap.class));
    }

    @Test
    @DisplayName("缺口检测 - 无缺口")
    void detectGaps_noGap_shouldReturnZero() {
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
    @DisplayName("缺口状态流转 - PENDING to FILLING")
    void updateStatus_pendingToFilling_shouldSucceed() {
        DataGap gap = createDataGap(1L, DataGapService.STATUS_PENDING);
        
        when(dataGapMapper.selectById(1L)).thenReturn(gap);
        
        dataGapService.updateStatus(1L, DataGapService.STATUS_FILLING);
        
        verify(dataGapMapper).updateStatus(1L, DataGapService.STATUS_FILLING);
    }

    @Test
    @DisplayName("缺口状态流转 - FILLED cannot change")
    void updateStatus_filledToPending_shouldFail() {
        DataGap gap = createDataGap(1L, DataGapService.STATUS_FILLED);
        
        when(dataGapMapper.selectById(1L)).thenReturn(gap);
        
        assertThrows(BusinessException.class, 
                () -> dataGapService.updateStatus(1L, DataGapService.STATUS_PENDING));
    }

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
                .closeTime(openTime.plus(1, ChronoUnit.HOURS).minusMillis(1))
                .build();
    }

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
}

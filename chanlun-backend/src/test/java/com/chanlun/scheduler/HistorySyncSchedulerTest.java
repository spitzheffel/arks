package com.chanlun.scheduler;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.service.HistorySyncService;
import com.chanlun.service.SyncFilterService;
import com.chanlun.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 历史数据同步定时任务测试
 */
@ExtendWith(MockitoExtension.class)
class HistorySyncSchedulerTest {

    @Mock
    private HistorySyncService historySyncService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private SyncFilterService syncFilterService;

    @InjectMocks
    private HistorySyncScheduler historySyncScheduler;

    private HistorySyncService.IncrementalSyncSummary successSummary;

    @BeforeEach
    void setUp() {
        successSummary = new HistorySyncService.IncrementalSyncSummary();
        successSummary.addSuccess(1L, "1m", 100);
        successSummary.addSuccess(1L, "1h", 50);
    }

    @Test
    @DisplayName("执行历史同步 - 自动同步关闭时跳过")
    void executeHistorySync_WhenAutoSyncDisabled_Skips() {
        when(systemConfigService.isHistoryAutoSyncEnabled()).thenReturn(false);

        historySyncScheduler.executeHistorySync();

        verify(historySyncService, never()).syncAllIncremental();
    }

    @Test
    @DisplayName("执行历史同步 - 自动同步开启时执行")
    void executeHistorySync_WhenAutoSyncEnabled_Executes() {
        when(systemConfigService.isHistoryAutoSyncEnabled()).thenReturn(true);
        when(historySyncService.syncAllIncremental()).thenReturn(successSummary);

        historySyncScheduler.executeHistorySync();

        verify(historySyncService).syncAllIncremental();
    }

    @Test
    @DisplayName("执行历史同步 - 异常处理")
    void executeHistorySync_ExceptionHandling() {
        when(systemConfigService.isHistoryAutoSyncEnabled()).thenReturn(true);
        when(historySyncService.syncAllIncremental()).thenThrow(new RuntimeException("同步错误"));

        // 不应抛出异常
        assertDoesNotThrow(() -> historySyncScheduler.executeHistorySync());
    }

    @Test
    @DisplayName("手动触发同步 - 不检查开关")
    void triggerManualSync_DoesNotCheckSwitch() {
        when(historySyncService.syncAllIncremental()).thenReturn(successSummary);

        HistorySyncService.IncrementalSyncSummary result = historySyncScheduler.triggerManualSync();

        assertNotNull(result);
        assertEquals(2, result.getTotalSymbols());
        assertEquals(2, result.getSuccessCount());
        assertEquals(150, result.getTotalKlines());
        
        // 不应检查自动同步开关
        verify(systemConfigService, never()).isHistoryAutoSyncEnabled();
    }

    @Test
    @DisplayName("获取待同步交易对数量")
    void getPendingSyncCount() {
        List<SymbolDTO> targets = Arrays.asList(
                SymbolDTO.builder().id(1L).symbol("BTCUSDT").build(),
                SymbolDTO.builder().id(2L).symbol("ETHUSDT").build()
        );
        when(syncFilterService.getHistorySyncTargets()).thenReturn(targets);

        int count = historySyncScheduler.getPendingSyncCount();

        assertEquals(2, count);
    }

    @Test
    @DisplayName("获取待同步交易对数量 - 无待同步")
    void getPendingSyncCount_NoTargets() {
        when(syncFilterService.getHistorySyncTargets()).thenReturn(Collections.emptyList());

        int count = historySyncScheduler.getPendingSyncCount();

        assertEquals(0, count);
    }
}

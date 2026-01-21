package com.chanlun.scheduler;

import com.chanlun.dto.GapDetectResult;
import com.chanlun.service.DataGapService;
import com.chanlun.service.GapFillService;
import com.chanlun.service.GapFillService.BatchGapFillResult;
import com.chanlun.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据缺口检测定时任务测试
 */
@ExtendWith(MockitoExtension.class)
class GapDetectSchedulerTest {

    @Mock
    private DataGapService dataGapService;

    @Mock
    private GapFillService gapFillService;

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private GapDetectScheduler gapDetectScheduler;

    private GapDetectResult successResult;

    @BeforeEach
    void setUp() {
        successResult = GapDetectResult.success(
                "缺口检测完成",
                5,   // symbolCount
                10,  // intervalCount
                3,   // newGapCount
                15,  // totalGapCount
                Collections.emptyList()
        );
    }

    @Test
    @DisplayName("执行缺口检测 - 正常执行")
    void executeGapDetect_Success() {
        when(dataGapService.detectAllGaps()).thenReturn(successResult);

        gapDetectScheduler.executeGapDetect();

        verify(dataGapService).detectAllGaps();
    }

    @Test
    @DisplayName("执行缺口检测 - 异常处理")
    void executeGapDetect_ExceptionHandling() {
        when(dataGapService.detectAllGaps()).thenThrow(new RuntimeException("检测错误"));

        // 不应抛出异常
        assertDoesNotThrow(() -> gapDetectScheduler.executeGapDetect());
    }

    @Test
    @DisplayName("手动触发缺口检测")
    void triggerManualDetect() {
        when(dataGapService.detectAllGaps()).thenReturn(successResult);

        GapDetectResult result = gapDetectScheduler.triggerManualDetect();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(5, result.getSymbolCount());
        assertEquals(10, result.getIntervalCount());
        assertEquals(3, result.getNewGapCount());
        assertEquals(15, result.getTotalGapCount());
    }

    @Test
    @DisplayName("获取缺口检测 Cron 表达式")
    void getGapDetectCron() {
        String expectedCron = "0 0 * * * ?";
        when(systemConfigService.getGapDetectCron()).thenReturn(expectedCron);

        String cron = gapDetectScheduler.getGapDetectCron();

        assertEquals(expectedCron, cron);
    }

    @Test
    @DisplayName("获取待处理缺口数量")
    void getPendingGapCount() {
        when(dataGapService.countPending()).thenReturn(10L);

        long count = gapDetectScheduler.getPendingGapCount();

        assertEquals(10L, count);
    }

    @Test
    @DisplayName("获取待处理缺口数量 - 无待处理")
    void getPendingGapCount_NoGaps() {
        when(dataGapService.countPending()).thenReturn(0L);

        long count = gapDetectScheduler.getPendingGapCount();

        assertEquals(0L, count);
    }

    @Nested
    @DisplayName("自动缺口回补测试")
    class AutoGapFillTest {

        @Test
        @DisplayName("执行自动回补 - 全局开关关闭时跳过")
        void executeAutoGapFill_GlobalDisabled() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);

            gapDetectScheduler.executeAutoGapFill();

            verify(gapFillService, never()).autoFillGaps();
        }

        @Test
        @DisplayName("执行自动回补 - 正常执行")
        void executeAutoGapFill_Success() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            BatchGapFillResult result = new BatchGapFillResult();
            result.addSuccess(GapFillService.GapFillResult.success(1L, 10, "成功"));
            when(gapFillService.autoFillGaps()).thenReturn(result);

            gapDetectScheduler.executeAutoGapFill();

            verify(gapFillService).autoFillGaps();
        }

        @Test
        @DisplayName("执行自动回补 - 异常处理")
        void executeAutoGapFill_ExceptionHandling() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);
            when(gapFillService.autoFillGaps()).thenThrow(new RuntimeException("回补错误"));

            assertDoesNotThrow(() -> gapDetectScheduler.executeAutoGapFill());
        }

        @Test
        @DisplayName("手动触发自动回补")
        void triggerManualAutoFill() {
            BatchGapFillResult expectedResult = new BatchGapFillResult();
            expectedResult.addSuccess(GapFillService.GapFillResult.success(1L, 5, "成功"));
            when(gapFillService.autoFillGaps()).thenReturn(expectedResult);

            BatchGapFillResult result = gapDetectScheduler.triggerManualAutoFill();

            assertNotNull(result);
            assertEquals(1, result.getSuccessCount());
        }

        @Test
        @DisplayName("检查自动回补是否启用")
        void isAutoGapFillEnabled() {
            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(true);

            assertTrue(gapDetectScheduler.isAutoGapFillEnabled());

            when(systemConfigService.isAutoGapFillEnabled()).thenReturn(false);

            assertFalse(gapDetectScheduler.isAutoGapFillEnabled());
        }
    }
}

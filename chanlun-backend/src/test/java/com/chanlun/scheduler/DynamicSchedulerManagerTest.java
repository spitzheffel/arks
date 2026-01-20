package com.chanlun.scheduler;

import com.chanlun.entity.SystemConfig;
import com.chanlun.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 动态定时任务管理器测试
 */
@ExtendWith(MockitoExtension.class)
class DynamicSchedulerManagerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private SymbolSyncScheduler symbolSyncScheduler;

    @Mock
    private HistorySyncScheduler historySyncScheduler;

    @Mock
    private GapDetectScheduler gapDetectScheduler;

    @Mock
    private ScheduledFuture<?> scheduledFuture;

    private DynamicSchedulerManager dynamicSchedulerManager;

    @BeforeEach
    void setUp() {
        dynamicSchedulerManager = new DynamicSchedulerManager(
                taskScheduler, systemConfigService, symbolSyncScheduler, historySyncScheduler, gapDetectScheduler);
    }

    @Test
    @DisplayName("调度交易对同步任务")
    void scheduleSymbolSync() {
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleSymbolSync();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        assertTrue(dynamicSchedulerManager.isTaskScheduled("symbolSync"));
        assertEquals("0 0 2 * * ?", dynamicSchedulerManager.getCurrentCronExpression("symbolSync"));
    }

    @Test
    @DisplayName("调度历史同步任务")
    void scheduleHistorySync() {
        when(systemConfigService.getHistorySyncCron()).thenReturn("0 30 3 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleHistorySync();

        verify(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        assertTrue(dynamicSchedulerManager.isTaskScheduled("historySync"));
    }

    @Test
    @DisplayName("取消任务")
    void cancelTask() {
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleSymbolSync();
        dynamicSchedulerManager.cancelTask("symbolSync");

        verify(scheduledFuture).cancel(false);
        assertFalse(dynamicSchedulerManager.isTaskScheduled("symbolSync"));
    }

    @Test
    @DisplayName("取消所有任务")
    void cancelAllTasks() {
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        when(systemConfigService.getHistorySyncCron()).thenReturn("0 30 3 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleSymbolSync();
        dynamicSchedulerManager.scheduleHistorySync();
        dynamicSchedulerManager.cancelAllTasks();

        verify(scheduledFuture, times(2)).cancel(false);
        assertTrue(dynamicSchedulerManager.getScheduledTaskNames().isEmpty());
    }

    @Test
    @DisplayName("刷新所有配置 - Cron 表达式变化时重新调度")
    void refreshAllConfigs_CronChanged() {
        // 初始调度
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));
        dynamicSchedulerManager.scheduleSymbolSync();

        // 模拟配置变化
        when(systemConfigService.getValueForceRefresh(SystemConfig.Keys.SYNC_SYMBOL_CRON))
                .thenReturn("0 0 3 * * ?");

        dynamicSchedulerManager.refreshAllConfigs();

        verify(systemConfigService).refreshCache();
    }

    @Test
    @DisplayName("获取已调度的任务名称")
    void getScheduledTaskNames() {
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleSymbolSync();

        assertTrue(dynamicSchedulerManager.getScheduledTaskNames().contains("symbolSync"));
    }

    @Test
    @DisplayName("检查任务是否已调度 - 未调度")
    void isTaskScheduled_NotScheduled() {
        assertFalse(dynamicSchedulerManager.isTaskScheduled("symbolSync"));
    }

    @Test
    @DisplayName("获取当前 Cron 表达式 - 未调度返回 null")
    void getCurrentCronExpression_NotScheduled() {
        assertNull(dynamicSchedulerManager.getCurrentCronExpression("symbolSync"));
    }

    @Test
    @DisplayName("重复调度同一任务会取消旧任务")
    void scheduleTask_CancelsOldTask() {
        when(systemConfigService.getSymbolSyncCron()).thenReturn("0 0 2 * * ?");
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(CronTrigger.class));

        dynamicSchedulerManager.scheduleSymbolSync();
        dynamicSchedulerManager.scheduleSymbolSync();

        verify(scheduledFuture, times(1)).cancel(false);
    }
}

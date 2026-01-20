package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.entity.SyncStatus;
import com.chanlun.entity.SyncTask;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.SyncStatusMapper;
import com.chanlun.mapper.SyncTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SyncService 测试
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SyncService 测试")
class SyncServiceTest {

    @Mock
    private SyncTaskMapper syncTaskMapper;

    @Mock
    private SyncStatusMapper syncStatusMapper;

    @InjectMocks
    private SyncService syncService;

    private Instant baseTime;

    @BeforeEach
    void setUp() {
        baseTime = Instant.parse("2025-01-01T00:00:00Z");
    }

    // ==================== 同步任务创建测试 ====================

    @Test
    @DisplayName("创建历史同步任务 - 成功")
    void createHistoryTask_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(24, ChronoUnit.HOURS);
        when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);

        SyncTask task = syncService.createHistoryTask(1L, "1h", startTime, endTime);

        assertNotNull(task);
        assertEquals(1L, task.getSymbolId());
        assertEquals("1h", task.getInterval());
        assertEquals(SyncTask.TaskType.HISTORY, task.getTaskType());
        assertEquals(SyncTask.Status.PENDING, task.getStatus());
        assertEquals(startTime, task.getStartTime());
        assertEquals(endTime, task.getEndTime());
        assertEquals(0, task.getSyncedCount());
        assertEquals(0, task.getRetryCount());
        assertEquals(3, task.getMaxRetries());
        verify(syncTaskMapper).insert(any(SyncTask.class));
    }

    @Test
    @DisplayName("创建缺口回补任务 - 成功")
    void createGapFillTask_success() {
        Instant startTime = baseTime;
        Instant endTime = baseTime.plus(1, ChronoUnit.HOURS);
        when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);

        SyncTask task = syncService.createGapFillTask(1L, "1h", startTime, endTime);

        assertNotNull(task);
        assertEquals(SyncTask.TaskType.GAP_FILL, task.getTaskType());
        verify(syncTaskMapper).insert(any(SyncTask.class));
    }

    @Test
    @DisplayName("创建实时同步任务 - 成功")
    void createRealtimeTask_success() {
        when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);

        SyncTask task = syncService.createRealtimeTask(1L, "1m");

        assertNotNull(task);
        assertEquals(SyncTask.TaskType.REALTIME, task.getTaskType());
        assertNull(task.getStartTime());
        assertNull(task.getEndTime());
        verify(syncTaskMapper).insert(any(SyncTask.class));
    }

    @Test
    @DisplayName("创建任务 - symbolId为空应抛出异常")
    void createTask_nullSymbolId_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(null, "1h", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("创建任务 - interval为空应抛出异常")
    void createTask_nullInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(1L, null, baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("创建任务 - 不支持的周期应抛出异常")
    void createTask_invalidInterval_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(1L, "1s", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("创建任务 - startTime为空应抛出异常")
    void createTask_nullStartTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(1L, "1h", null, baseTime.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @DisplayName("创建任务 - endTime为空应抛出异常")
    void createTask_nullEndTime_shouldThrowException() {
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(1L, "1h", baseTime, null));
    }

    @Test
    @DisplayName("创建任务 - startTime晚于endTime应抛出异常")
    void createTask_invalidTimeRange_shouldThrowException() {
        Instant startTime = baseTime.plus(24, ChronoUnit.HOURS);
        Instant endTime = baseTime;
        assertThrows(BusinessException.class, 
                () -> syncService.createHistoryTask(1L, "1h", startTime, endTime));
    }

    // ==================== 任务状态更新测试 ====================

    @Test
    @DisplayName("启动任务 - 成功")
    void startTask_success() {
        when(syncTaskMapper.updateStatus(1L, SyncTask.Status.RUNNING)).thenReturn(1);

        boolean result = syncService.startTask(1L);

        assertTrue(result);
        verify(syncTaskMapper).updateStatus(1L, SyncTask.Status.RUNNING);
    }

    @Test
    @DisplayName("完成任务 - 成功")
    void completeTask_success() {
        SyncTask task = SyncTask.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .status(SyncTask.Status.RUNNING)
                .build();
        when(syncTaskMapper.selectById(1L)).thenReturn(task);
        when(syncTaskMapper.updateById(any(SyncTask.class))).thenReturn(1);

        boolean result = syncService.completeTask(1L, 100);

        assertTrue(result);
        assertEquals(SyncTask.Status.SUCCESS, task.getStatus());
        assertEquals(100, task.getSyncedCount());
    }

    @Test
    @DisplayName("完成任务 - 任务不存在应返回false")
    void completeTask_notFound_shouldReturnFalse() {
        when(syncTaskMapper.selectById(1L)).thenReturn(null);

        boolean result = syncService.completeTask(1L, 100);

        assertFalse(result);
    }

    @Test
    @DisplayName("失败任务 - 成功")
    void failTask_success() {
        when(syncTaskMapper.updateStatusAndError(1L, SyncTask.Status.FAILED, "Test error")).thenReturn(1);

        boolean result = syncService.failTask(1L, "Test error");

        assertTrue(result);
        verify(syncTaskMapper).updateStatusAndError(1L, SyncTask.Status.FAILED, "Test error");
    }

    @Test
    @DisplayName("重试任务 - 成功")
    void retryTask_success() {
        SyncTask task = SyncTask.builder()
                .id(1L)
                .retryCount(1)
                .maxRetries(3)
                .build();
        when(syncTaskMapper.selectById(1L)).thenReturn(task);
        when(syncTaskMapper.incrementRetryCount(1L)).thenReturn(1);
        when(syncTaskMapper.updateStatus(1L, SyncTask.Status.PENDING)).thenReturn(1);

        boolean result = syncService.retryTask(1L);

        assertTrue(result);
        verify(syncTaskMapper).incrementRetryCount(1L);
        verify(syncTaskMapper).updateStatus(1L, SyncTask.Status.PENDING);
    }

    @Test
    @DisplayName("重试任务 - 达到最大重试次数应返回false")
    void retryTask_maxRetriesReached_shouldReturnFalse() {
        SyncTask task = SyncTask.builder()
                .id(1L)
                .retryCount(3)
                .maxRetries(3)
                .build();
        when(syncTaskMapper.selectById(1L)).thenReturn(task);

        boolean result = syncService.retryTask(1L);

        assertFalse(result);
        verify(syncTaskMapper, never()).incrementRetryCount(anyLong());
    }

    @Test
    @DisplayName("重试任务 - 任务不存在应返回false")
    void retryTask_notFound_shouldReturnFalse() {
        when(syncTaskMapper.selectById(1L)).thenReturn(null);

        boolean result = syncService.retryTask(1L);

        assertFalse(result);
    }

    // ==================== 同步状态管理测试 ====================

    @Test
    @DisplayName("获取或创建同步状态 - 已存在")
    void getOrCreateSyncStatus_exists() {
        SyncStatus existing = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .totalKlines(1000L)
                .autoGapFillEnabled(true)
                .build();
        when(syncStatusMapper.selectBySymbolIdAndInterval(100L, "1h")).thenReturn(existing);

        SyncStatus result = syncService.getOrCreateSyncStatus(100L, "1h");

        assertEquals(existing, result);
        verify(syncStatusMapper, never()).insert(any(SyncStatus.class));
    }

    @Test
    @DisplayName("获取或创建同步状态 - 不存在则创建")
    void getOrCreateSyncStatus_notExists_shouldCreate() {
        when(syncStatusMapper.selectBySymbolIdAndInterval(100L, "1h")).thenReturn(null);
        doReturn(1).when(syncStatusMapper).insert(any(SyncStatus.class));

        SyncStatus result = syncService.getOrCreateSyncStatus(100L, "1h");

        assertNotNull(result);
        assertEquals(100L, result.getSymbolId());
        assertEquals("1h", result.getInterval());
        assertEquals(0L, result.getTotalKlines());
        assertTrue(result.getAutoGapFillEnabled());
        verify(syncStatusMapper).insert(any(SyncStatus.class));
    }

    @Test
    @DisplayName("更新同步状态 - 成功")
    void updateSyncStatus_success() {
        SyncStatus existing = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .totalKlines(1000L)
                .lastKlineTime(baseTime)
                .autoGapFillEnabled(true)
                .build();
        when(syncStatusMapper.selectBySymbolIdAndInterval(100L, "1h")).thenReturn(existing);
        when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

        Instant newLastKlineTime = baseTime.plus(1, ChronoUnit.HOURS);
        syncService.updateSyncStatus(100L, "1h", newLastKlineTime, 10);

        assertEquals(newLastKlineTime, existing.getLastKlineTime());
        assertEquals(1010L, existing.getTotalKlines());
        verify(syncStatusMapper).updateById(existing);
    }

    @Test
    @DisplayName("更新同步状态 - lastKlineTime取max")
    void updateSyncStatus_lastKlineTimeTakesMax() {
        Instant existingTime = baseTime.plus(2, ChronoUnit.HOURS);
        SyncStatus existing = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .totalKlines(1000L)
                .lastKlineTime(existingTime)
                .autoGapFillEnabled(true)
                .build();
        when(syncStatusMapper.selectBySymbolIdAndInterval(100L, "1h")).thenReturn(existing);
        when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

        // 传入更早的时间，应该保持原来的时间
        Instant earlierTime = baseTime.plus(1, ChronoUnit.HOURS);
        syncService.updateSyncStatus(100L, "1h", earlierTime, 10);

        assertEquals(existingTime, existing.getLastKlineTime());
    }

    @Test
    @DisplayName("更新自动回补开关 - 成功")
    void updateAutoGapFillEnabled_success() {
        SyncStatus existing = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .autoGapFillEnabled(true)
                .build();
        when(syncStatusMapper.selectBySymbolIdAndInterval(100L, "1h")).thenReturn(existing);
        when(syncStatusMapper.updateAutoGapFillEnabled(100L, "1h", false)).thenReturn(1);

        boolean result = syncService.updateAutoGapFillEnabled(100L, "1h", false);

        assertTrue(result);
        verify(syncStatusMapper).updateAutoGapFillEnabled(100L, "1h", false);
    }

    @Test
    @DisplayName("根据ID更新自动回补开关 - 成功")
    void updateAutoGapFillEnabledById_success() {
        SyncStatus existing = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .autoGapFillEnabled(true)
                .build();
        when(syncStatusMapper.selectById(1L)).thenReturn(existing);
        when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

        boolean result = syncService.updateAutoGapFillEnabledById(1L, false);

        assertTrue(result);
        assertFalse(existing.getAutoGapFillEnabled());
    }

    @Test
    @DisplayName("根据ID更新自动回补开关 - 不存在应抛出异常")
    void updateAutoGapFillEnabledById_notFound_shouldThrowException() {
        when(syncStatusMapper.selectById(1L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, 
                () -> syncService.updateAutoGapFillEnabledById(1L, false));
    }

    @Test
    @DisplayName("重置同步状态 - 成功")
    void resetSyncStatus_success() {
        when(syncStatusMapper.updateLastKlineTime(100L, "1h", null)).thenReturn(1);
        when(syncStatusMapper.updateTotalKlines(100L, "1h", 0L)).thenReturn(1);
        when(syncStatusMapper.updateAutoGapFillEnabled(100L, "1h", false)).thenReturn(1);

        syncService.resetSyncStatus(100L, "1h", null, 0L);

        verify(syncStatusMapper).updateLastKlineTime(100L, "1h", null);
        verify(syncStatusMapper).updateTotalKlines(100L, "1h", 0L);
        verify(syncStatusMapper).updateAutoGapFillEnabled(100L, "1h", false);
    }

    // ==================== 查询测试 ====================

    @Test
    @DisplayName("获取待处理任务 - 成功")
    void getPendingTasks_success() {
        List<SyncTask> tasks = List.of(
                SyncTask.builder().id(1L).status(SyncTask.Status.PENDING).build(),
                SyncTask.builder().id(2L).status(SyncTask.Status.PENDING).build()
        );
        when(syncTaskMapper.selectPendingTasks(10)).thenReturn(tasks);

        List<SyncTask> result = syncService.getPendingTasks(10);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("获取运行中任务 - 成功")
    void getRunningTasks_success() {
        List<SyncTask> tasks = List.of(
                SyncTask.builder().id(1L).status(SyncTask.Status.RUNNING).build()
        );
        when(syncTaskMapper.selectRunningTasks()).thenReturn(tasks);

        List<SyncTask> result = syncService.getRunningTasks();

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("获取可重试任务 - 成功")
    void getRetryableTasks_success() {
        List<SyncTask> tasks = List.of(
                SyncTask.builder().id(1L).status(SyncTask.Status.FAILED).retryCount(1).maxRetries(3).build()
        );
        when(syncTaskMapper.selectRetryableTasks(10)).thenReturn(tasks);

        List<SyncTask> result = syncService.getRetryableTasks(10);

        assertEquals(1, result.size());
    }
}

package com.chanlun.acceptance;

import com.chanlun.entity.SyncStatus;
import com.chanlun.entity.SyncTask;
import com.chanlun.exception.BusinessException;
import com.chanlun.mapper.SyncStatusMapper;
import com.chanlun.mapper.SyncTaskMapper;
import com.chanlun.service.SyncService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 同步状态验收测试 (任务 25.5, 25.10, 25.16)
 * 
 * 验证同步状态相关功能：
 * - 25.5 同步对象筛选
 * - 25.10 手动历史同步筛选规则
 * - 25.16 历史同步更新 sync_status
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("同步状态验收测试")
class SyncStatusAcceptanceTest {

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

    // ==================== 25.5 验证同步对象筛选 ====================

    @Nested
    @DisplayName("25.5 验证同步对象筛选")
    class SyncObjectFilterTests {

        @Test
        @DisplayName("创建历史同步任务 - 校验 symbolId 不能为空")
        void createHistoryTask_nullSymbolId_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> syncService.createHistoryTask(null, "1h", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }

        @Test
        @DisplayName("创建历史同步任务 - 校验 interval 不能为空")
        void createHistoryTask_nullInterval_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> syncService.createHistoryTask(1L, null, baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }

        @Test
        @DisplayName("创建历史同步任务 - 校验 interval 不能为空字符串")
        void createHistoryTask_emptyInterval_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> syncService.createHistoryTask(1L, "", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }

        @Test
        @DisplayName("创建历史同步任务 - 拒绝无效周期")
        void createHistoryTask_invalidInterval_shouldThrowException() {
            assertThrows(BusinessException.class,
                    () -> syncService.createHistoryTask(1L, "1s", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
            assertThrows(BusinessException.class,
                    () -> syncService.createHistoryTask(1L, "invalid", baseTime, baseTime.plus(1, ChronoUnit.HOURS)));
        }
    }

    // ==================== 25.10 验证手动历史同步筛选规则 ====================

    @Nested
    @DisplayName("25.10 验证手动历史同步筛选规则")
    class ManualHistorySyncFilterTests {

        @Test
        @DisplayName("创建历史同步任务 - 成功")
        void createHistoryTask_success() {
            Instant startTime = baseTime;
            Instant endTime = baseTime.plus(1, ChronoUnit.DAYS);

            when(syncTaskMapper.insert(any(SyncTask.class))).thenReturn(1);

            SyncTask task = syncService.createHistoryTask(1L, "1h", startTime, endTime);

            assertNotNull(task);
            assertEquals(1L, task.getSymbolId());
            assertEquals("1h", task.getInterval());
            assertEquals(SyncTask.TaskType.HISTORY, task.getTaskType());
            assertEquals(SyncTask.Status.PENDING, task.getStatus());
            assertEquals(startTime, task.getStartTime());
            assertEquals(endTime, task.getEndTime());
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
    }

    // ==================== 25.16 验证历史同步更新 sync_status ====================

    @Nested
    @DisplayName("25.16 验证历史同步更新 sync_status")
    class UpdateSyncStatusTests {

        @Test
        @DisplayName("更新同步状态 - 首次创建")
        void updateSyncStatus_createNew() {
            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(null);
            when(syncStatusMapper.insert(any(SyncStatus.class))).thenReturn(1);
            when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

            Instant lastKlineTime = baseTime.plus(1, ChronoUnit.DAYS);
            syncService.updateSyncStatus(1L, "1h", lastKlineTime, 100);

            verify(syncStatusMapper).insert(any(SyncStatus.class));
            verify(syncStatusMapper).updateById(any(SyncStatus.class));
        }

        @Test
        @DisplayName("更新同步状态 - 更新已有记录")
        void updateSyncStatus_updateExisting() {
            SyncStatus existingStatus = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(baseTime)
                    .totalKlines(50L)
                    .autoGapFillEnabled(true)
                    .build();

            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(existingStatus);
            when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

            Instant newLastKlineTime = baseTime.plus(1, ChronoUnit.DAYS);
            syncService.updateSyncStatus(1L, "1h", newLastKlineTime, 100);

            verify(syncStatusMapper).updateById(any(SyncStatus.class));
            // 验证 existingStatus 被正确更新
            assertEquals(newLastKlineTime, existingStatus.getLastKlineTime());
            assertEquals(150L, existingStatus.getTotalKlines()); // 50 + 100
        }

        @Test
        @DisplayName("更新同步状态 - last_kline_time 取 max")
        void updateSyncStatus_lastKlineTimeTakesMax() {
            Instant existingLastKlineTime = baseTime.plus(2, ChronoUnit.DAYS);
            SyncStatus existingStatus = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(existingLastKlineTime)
                    .totalKlines(100L)
                    .autoGapFillEnabled(true)
                    .build();

            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(existingStatus);
            when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

            // 新的 lastKlineTime 比已有的早
            Instant newLastKlineTime = baseTime.plus(1, ChronoUnit.DAYS);
            syncService.updateSyncStatus(1L, "1h", newLastKlineTime, 50);

            // 应该保留较大的 lastKlineTime
            verify(syncStatusMapper).updateById(any(SyncStatus.class));
            assertEquals(existingLastKlineTime, existingStatus.getLastKlineTime());
        }

        @Test
        @DisplayName("更新同步状态 - 增量更新 total_klines")
        void updateSyncStatus_incrementsTotalKlines() {
            SyncStatus existingStatus = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(baseTime)
                    .totalKlines(1000L)
                    .autoGapFillEnabled(true)
                    .build();

            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(existingStatus);
            when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

            syncService.updateSyncStatus(1L, "1h", baseTime.plus(1, ChronoUnit.HOURS), 24);

            verify(syncStatusMapper).updateById(any(SyncStatus.class));
            assertEquals(1024L, existingStatus.getTotalKlines()); // 1000 + 24
        }

        @Test
        @DisplayName("更新同步状态 - 设置 last_sync_time")
        void updateSyncStatus_setsLastSyncTime() {
            SyncStatus existingStatus = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .lastKlineTime(baseTime)
                    .totalKlines(100L)
                    .autoGapFillEnabled(true)
                    .build();

            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(existingStatus);
            when(syncStatusMapper.updateById(any(SyncStatus.class))).thenReturn(1);

            Instant beforeUpdate = Instant.now();
            syncService.updateSyncStatus(1L, "1h", baseTime.plus(1, ChronoUnit.HOURS), 10);

            verify(syncStatusMapper).updateById(any(SyncStatus.class));
            assertNotNull(existingStatus.getLastSyncTime());
            assertFalse(existingStatus.getLastSyncTime().isBefore(beforeUpdate));
        }
    }

    // ==================== 自动回补开关测试 ====================

    @Nested
    @DisplayName("自动回补开关测试")
    class AutoGapFillEnabledTests {

        @Test
        @DisplayName("更新自动回补开关 - 成功")
        void updateAutoGapFillEnabled_success() {
            SyncStatus existingStatus = SyncStatus.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .autoGapFillEnabled(true)
                    .build();

            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(existingStatus);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false)).thenReturn(1);

            boolean result = syncService.updateAutoGapFillEnabled(1L, "1h", false);

            assertTrue(result);
            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
        }

        @Test
        @DisplayName("更新自动回补开关 - 自动创建同步状态")
        void updateAutoGapFillEnabled_createsStatusIfNotExists() {
            when(syncStatusMapper.selectBySymbolIdAndInterval(1L, "1h")).thenReturn(null);
            when(syncStatusMapper.insert(any(SyncStatus.class))).thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", true)).thenReturn(1);

            boolean result = syncService.updateAutoGapFillEnabled(1L, "1h", true);

            assertTrue(result);
            verify(syncStatusMapper).insert(any(SyncStatus.class));
            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", true);
        }

        @Test
        @DisplayName("重置同步状态 - 关闭自动回补")
        void resetSyncStatus_disablesAutoGapFill() {
            when(syncStatusMapper.updateLastKlineTime(1L, "1h", null)).thenReturn(1);
            when(syncStatusMapper.updateTotalKlines(1L, "1h", 0L)).thenReturn(1);
            when(syncStatusMapper.updateAutoGapFillEnabled(1L, "1h", false)).thenReturn(1);

            syncService.resetSyncStatus(1L, "1h", null, 0L);

            verify(syncStatusMapper).updateAutoGapFillEnabled(1L, "1h", false);
        }
    }

    // ==================== 任务状态流转测试 ====================

    @Nested
    @DisplayName("任务状态流转测试")
    class TaskStatusTransitionTests {

        @Test
        @DisplayName("任务状态流转 - PENDING -> RUNNING")
        void taskStatus_pendingToRunning() {
            when(syncTaskMapper.updateStatus(1L, SyncTask.Status.RUNNING)).thenReturn(1);

            boolean result = syncService.startTask(1L);

            assertTrue(result);
            verify(syncTaskMapper).updateStatus(1L, SyncTask.Status.RUNNING);
        }

        @Test
        @DisplayName("任务状态流转 - RUNNING -> SUCCESS")
        void taskStatus_runningToSuccess() {
            SyncTask task = SyncTask.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .taskType(SyncTask.TaskType.HISTORY)
                    .status(SyncTask.Status.RUNNING)
                    .syncedCount(0)
                    .build();

            when(syncTaskMapper.selectById(1L)).thenReturn(task);
            when(syncTaskMapper.updateById(any(SyncTask.class))).thenReturn(1);

            boolean result = syncService.completeTask(1L, 100);

            assertTrue(result);
            verify(syncTaskMapper).updateById(any(SyncTask.class));
            assertEquals(SyncTask.Status.SUCCESS, task.getStatus());
            assertEquals(100, task.getSyncedCount());
        }

        @Test
        @DisplayName("任务状态流转 - RUNNING -> FAILED")
        void taskStatus_runningToFailed() {
            when(syncTaskMapper.updateStatusAndError(1L, SyncTask.Status.FAILED, "Test error"))
                    .thenReturn(1);

            boolean result = syncService.failTask(1L, "Test error");

            assertTrue(result);
            verify(syncTaskMapper).updateStatusAndError(1L, SyncTask.Status.FAILED, "Test error");
        }

        @Test
        @DisplayName("任务重试 - 未达到最大重试次数")
        void taskRetry_withinMaxRetries() {
            SyncTask task = SyncTask.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .taskType(SyncTask.TaskType.HISTORY)
                    .status(SyncTask.Status.FAILED)
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
        @DisplayName("任务重试 - 达到最大重试次数")
        void taskRetry_reachedMaxRetries() {
            SyncTask task = SyncTask.builder()
                    .id(1L)
                    .symbolId(1L)
                    .interval("1h")
                    .taskType(SyncTask.TaskType.HISTORY)
                    .status(SyncTask.Status.FAILED)
                    .retryCount(3)
                    .maxRetries(3)
                    .build();

            when(syncTaskMapper.selectById(1L)).thenReturn(task);

            boolean result = syncService.retryTask(1L);

            assertFalse(result);
            verify(syncTaskMapper, never()).incrementRetryCount(anyLong());
        }
    }
}

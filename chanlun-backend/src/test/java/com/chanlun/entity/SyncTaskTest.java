package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyncTask 实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("SyncTask 实体测试")
class SyncTaskTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant now = Instant.now();
        SyncTask task = SyncTask.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .taskType(SyncTask.TaskType.HISTORY)
                .status(SyncTask.Status.PENDING)
                .startTime(now)
                .endTime(now.plusSeconds(3600))
                .syncedCount(0)
                .retryCount(0)
                .maxRetries(3)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, task.getId());
        assertEquals(100L, task.getSymbolId());
        assertEquals("1h", task.getInterval());
        assertEquals(SyncTask.TaskType.HISTORY, task.getTaskType());
        assertEquals(SyncTask.Status.PENDING, task.getStatus());
        assertEquals(0, task.getSyncedCount());
        assertEquals(0, task.getRetryCount());
        assertEquals(3, task.getMaxRetries());
    }

    @Test
    @DisplayName("任务类型常量应正确定义")
    void taskType_shouldHaveCorrectValues() {
        assertEquals("REALTIME", SyncTask.TaskType.REALTIME);
        assertEquals("HISTORY", SyncTask.TaskType.HISTORY);
        assertEquals("GAP_FILL", SyncTask.TaskType.GAP_FILL);
    }

    @Test
    @DisplayName("任务状态常量应正确定义")
    void status_shouldHaveCorrectValues() {
        assertEquals("PENDING", SyncTask.Status.PENDING);
        assertEquals("RUNNING", SyncTask.Status.RUNNING);
        assertEquals("SUCCESS", SyncTask.Status.SUCCESS);
        assertEquals("FAILED", SyncTask.Status.FAILED);
    }

    @Test
    @DisplayName("无参构造函数应创建空实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        SyncTask task = new SyncTask();
        assertNull(task.getId());
        assertNull(task.getSymbolId());
        assertNull(task.getInterval());
    }

    @Test
    @DisplayName("Setter 方法应正确设置值")
    void setter_shouldSetValues() {
        SyncTask task = new SyncTask();
        task.setId(1L);
        task.setSymbolId(100L);
        task.setInterval("1h");
        task.setTaskType(SyncTask.TaskType.GAP_FILL);
        task.setStatus(SyncTask.Status.RUNNING);
        task.setSyncedCount(50);
        task.setRetryCount(1);
        task.setMaxRetries(3);
        task.setErrorMessage("Test error");

        assertEquals(1L, task.getId());
        assertEquals(100L, task.getSymbolId());
        assertEquals("1h", task.getInterval());
        assertEquals(SyncTask.TaskType.GAP_FILL, task.getTaskType());
        assertEquals(SyncTask.Status.RUNNING, task.getStatus());
        assertEquals(50, task.getSyncedCount());
        assertEquals(1, task.getRetryCount());
        assertEquals(3, task.getMaxRetries());
        assertEquals("Test error", task.getErrorMessage());
    }
}

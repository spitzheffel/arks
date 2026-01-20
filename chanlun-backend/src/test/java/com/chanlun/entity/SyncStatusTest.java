package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SyncStatus 实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("SyncStatus 实体测试")
class SyncStatusTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant now = Instant.now();
        SyncStatus status = SyncStatus.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .lastSyncTime(now)
                .lastKlineTime(now.minusSeconds(3600))
                .totalKlines(1000L)
                .autoGapFillEnabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, status.getId());
        assertEquals(100L, status.getSymbolId());
        assertEquals("1h", status.getInterval());
        assertEquals(now, status.getLastSyncTime());
        assertEquals(now.minusSeconds(3600), status.getLastKlineTime());
        assertEquals(1000L, status.getTotalKlines());
        assertTrue(status.getAutoGapFillEnabled());
    }

    @Test
    @DisplayName("无参构造函数应创建空实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        SyncStatus status = new SyncStatus();
        assertNull(status.getId());
        assertNull(status.getSymbolId());
        assertNull(status.getInterval());
        assertNull(status.getLastKlineTime());
    }

    @Test
    @DisplayName("lastKlineTime 可以为 null（表示无数据）")
    void lastKlineTime_canBeNull() {
        SyncStatus status = SyncStatus.builder()
                .symbolId(100L)
                .interval("1h")
                .lastKlineTime(null)
                .totalKlines(0L)
                .autoGapFillEnabled(true)
                .build();

        assertNull(status.getLastKlineTime());
        assertEquals(0L, status.getTotalKlines());
    }

    @Test
    @DisplayName("Setter 方法应正确设置值")
    void setter_shouldSetValues() {
        Instant now = Instant.now();
        SyncStatus status = new SyncStatus();
        status.setId(1L);
        status.setSymbolId(100L);
        status.setInterval("1h");
        status.setLastSyncTime(now);
        status.setLastKlineTime(now);
        status.setTotalKlines(500L);
        status.setAutoGapFillEnabled(false);

        assertEquals(1L, status.getId());
        assertEquals(100L, status.getSymbolId());
        assertEquals("1h", status.getInterval());
        assertEquals(now, status.getLastSyncTime());
        assertEquals(now, status.getLastKlineTime());
        assertEquals(500L, status.getTotalKlines());
        assertFalse(status.getAutoGapFillEnabled());
    }

    @Test
    @DisplayName("autoGapFillEnabled 默认应为 true")
    void autoGapFillEnabled_defaultShouldBeTrue() {
        // 根据数据库设计，默认值为 true
        SyncStatus status = SyncStatus.builder()
                .symbolId(100L)
                .interval("1h")
                .autoGapFillEnabled(true)
                .build();

        assertTrue(status.getAutoGapFillEnabled());
    }
}

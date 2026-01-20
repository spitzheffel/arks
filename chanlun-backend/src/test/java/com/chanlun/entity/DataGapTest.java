package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据缺口实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("DataGap 实体测试")
class DataGapTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant now = Instant.now();
        Instant gapStart = now.minusSeconds(3600);
        Instant gapEnd = now;

        DataGap gap = DataGap.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .gapStart(gapStart)
                .gapEnd(gapEnd)
                .missingCount(10)
                .status("PENDING")
                .retryCount(0)
                .errorMessage(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, gap.getId());
        assertEquals(100L, gap.getSymbolId());
        assertEquals("1h", gap.getInterval());
        assertEquals(gapStart, gap.getGapStart());
        assertEquals(gapEnd, gap.getGapEnd());
        assertEquals(10, gap.getMissingCount());
        assertEquals("PENDING", gap.getStatus());
        assertEquals(0, gap.getRetryCount());
        assertNull(gap.getErrorMessage());
        assertEquals(now, gap.getCreatedAt());
        assertEquals(now, gap.getUpdatedAt());
    }

    @Test
    @DisplayName("无参构造函数创建实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        DataGap gap = new DataGap();

        assertNull(gap.getId());
        assertNull(gap.getSymbolId());
        assertNull(gap.getInterval());
        assertNull(gap.getGapStart());
        assertNull(gap.getGapEnd());
        assertNull(gap.getMissingCount());
        assertNull(gap.getStatus());
        assertNull(gap.getRetryCount());
        assertNull(gap.getErrorMessage());
    }

    @Test
    @DisplayName("Setter 方法设置属性")
    void setters_shouldSetProperties() {
        DataGap gap = new DataGap();
        Instant now = Instant.now();

        gap.setId(1L);
        gap.setSymbolId(100L);
        gap.setInterval("1m");
        gap.setGapStart(now.minusSeconds(60));
        gap.setGapEnd(now);
        gap.setMissingCount(5);
        gap.setStatus("FILLING");
        gap.setRetryCount(1);
        gap.setErrorMessage("Test error");
        gap.setCreatedAt(now);
        gap.setUpdatedAt(now);

        assertEquals(1L, gap.getId());
        assertEquals(100L, gap.getSymbolId());
        assertEquals("1m", gap.getInterval());
        assertEquals(5, gap.getMissingCount());
        assertEquals("FILLING", gap.getStatus());
        assertEquals(1, gap.getRetryCount());
        assertEquals("Test error", gap.getErrorMessage());
    }

    @Test
    @DisplayName("equals 和 hashCode 方法")
    void equalsAndHashCode_shouldWorkCorrectly() {
        Instant now = Instant.now();
        
        DataGap gap1 = DataGap.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .gapStart(now)
                .gapEnd(now.plusSeconds(3600))
                .build();

        DataGap gap2 = DataGap.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .gapStart(now)
                .gapEnd(now.plusSeconds(3600))
                .build();

        DataGap gap3 = DataGap.builder()
                .id(2L)
                .symbolId(100L)
                .interval("1h")
                .gapStart(now)
                .gapEnd(now.plusSeconds(3600))
                .build();

        assertEquals(gap1, gap2);
        assertEquals(gap1.hashCode(), gap2.hashCode());
        assertNotEquals(gap1, gap3);
    }

    @Test
    @DisplayName("toString 方法")
    void toString_shouldReturnString() {
        DataGap gap = DataGap.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1h")
                .status("PENDING")
                .build();

        String str = gap.toString();

        assertTrue(str.contains("DataGap"));
        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("symbolId=100"));
        assertTrue(str.contains("interval=1h"));
        assertTrue(str.contains("status=PENDING"));
    }
}

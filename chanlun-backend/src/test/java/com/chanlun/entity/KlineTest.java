package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kline 实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("Kline 实体测试")
class KlineTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant openTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant closeTime = Instant.parse("2025-01-01T00:00:59.999Z");
        Instant createdAt = Instant.now();
        
        Kline kline = Kline.builder()
                .id(1L)
                .symbolId(100L)
                .interval("1m")
                .openTime(openTime)
                .open(new BigDecimal("50000.00000000"))
                .high(new BigDecimal("50100.00000000"))
                .low(new BigDecimal("49900.00000000"))
                .close(new BigDecimal("50050.00000000"))
                .volume(new BigDecimal("100.50000000"))
                .quoteVolume(new BigDecimal("5025000.00000000"))
                .trades(1500)
                .closeTime(closeTime)
                .createdAt(createdAt)
                .build();

        assertEquals(1L, kline.getId());
        assertEquals(100L, kline.getSymbolId());
        assertEquals("1m", kline.getInterval());
        assertEquals(openTime, kline.getOpenTime());
        assertEquals(new BigDecimal("50000.00000000"), kline.getOpen());
        assertEquals(new BigDecimal("50100.00000000"), kline.getHigh());
        assertEquals(new BigDecimal("49900.00000000"), kline.getLow());
        assertEquals(new BigDecimal("50050.00000000"), kline.getClose());
        assertEquals(new BigDecimal("100.50000000"), kline.getVolume());
        assertEquals(new BigDecimal("5025000.00000000"), kline.getQuoteVolume());
        assertEquals(1500, kline.getTrades());
        assertEquals(closeTime, kline.getCloseTime());
        assertEquals(createdAt, kline.getCreatedAt());
    }

    @Test
    @DisplayName("无参构造函数创建实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        Kline kline = new Kline();
        
        assertNull(kline.getId());
        assertNull(kline.getSymbolId());
        assertNull(kline.getInterval());
        assertNull(kline.getOpenTime());
        assertNull(kline.getOpen());
        assertNull(kline.getHigh());
        assertNull(kline.getLow());
        assertNull(kline.getClose());
        assertNull(kline.getVolume());
        assertNull(kline.getQuoteVolume());
        assertNull(kline.getTrades());
        assertNull(kline.getCloseTime());
        assertNull(kline.getCreatedAt());
    }

    @Test
    @DisplayName("Setter 方法设置属性")
    void setter_shouldSetProperties() {
        Kline kline = new Kline();
        Instant openTime = Instant.parse("2025-01-01T01:00:00Z");
        Instant closeTime = Instant.parse("2025-01-01T01:59:59.999Z");
        
        kline.setId(2L);
        kline.setSymbolId(200L);
        kline.setInterval("1h");
        kline.setOpenTime(openTime);
        kline.setOpen(new BigDecimal("3000.00"));
        kline.setHigh(new BigDecimal("3100.00"));
        kline.setLow(new BigDecimal("2900.00"));
        kline.setClose(new BigDecimal("3050.00"));
        kline.setVolume(new BigDecimal("500.00"));
        kline.setQuoteVolume(new BigDecimal("1500000.00"));
        kline.setTrades(2000);
        kline.setCloseTime(closeTime);

        assertEquals(2L, kline.getId());
        assertEquals(200L, kline.getSymbolId());
        assertEquals("1h", kline.getInterval());
        assertEquals(openTime, kline.getOpenTime());
        assertEquals(new BigDecimal("3000.00"), kline.getOpen());
        assertEquals(new BigDecimal("3100.00"), kline.getHigh());
        assertEquals(new BigDecimal("2900.00"), kline.getLow());
        assertEquals(new BigDecimal("3050.00"), kline.getClose());
        assertEquals(new BigDecimal("500.00"), kline.getVolume());
        assertEquals(new BigDecimal("1500000.00"), kline.getQuoteVolume());
        assertEquals(2000, kline.getTrades());
        assertEquals(closeTime, kline.getCloseTime());
    }

    @Test
    @DisplayName("全参构造函数创建实体")
    void allArgsConstructor_shouldCreateEntity() {
        Instant openTime = Instant.parse("2025-01-01T00:00:00Z");
        Instant closeTime = Instant.parse("2025-01-01T23:59:59.999Z");
        Instant createdAt = Instant.now();
        
        Kline kline = new Kline(
                3L, 300L, "1d", openTime,
                new BigDecimal("40000.00"), new BigDecimal("42000.00"),
                new BigDecimal("39000.00"), new BigDecimal("41000.00"),
                new BigDecimal("10000.00"), new BigDecimal("400000000.00"),
                50000, closeTime, createdAt
        );

        assertEquals(3L, kline.getId());
        assertEquals(300L, kline.getSymbolId());
        assertEquals("1d", kline.getInterval());
        assertEquals(openTime, kline.getOpenTime());
        assertEquals(new BigDecimal("40000.00"), kline.getOpen());
        assertEquals(new BigDecimal("42000.00"), kline.getHigh());
        assertEquals(new BigDecimal("39000.00"), kline.getLow());
        assertEquals(new BigDecimal("41000.00"), kline.getClose());
        assertEquals(new BigDecimal("10000.00"), kline.getVolume());
        assertEquals(new BigDecimal("400000000.00"), kline.getQuoteVolume());
        assertEquals(50000, kline.getTrades());
        assertEquals(closeTime, kline.getCloseTime());
        assertEquals(createdAt, kline.getCreatedAt());
    }

    @Test
    @DisplayName("支持的时间周期")
    void interval_shouldSupportAllPeriods() {
        String[] intervals = {"1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "6h", "8h", "12h", "1d", "3d", "1w", "1M"};
        
        for (String interval : intervals) {
            Kline kline = Kline.builder()
                    .interval(interval)
                    .build();
            assertEquals(interval, kline.getInterval());
        }
    }

    @Test
    @DisplayName("BigDecimal 精度保持")
    void bigDecimal_shouldMaintainPrecision() {
        BigDecimal price = new BigDecimal("50000.12345678");
        
        Kline kline = Kline.builder()
                .open(price)
                .high(price)
                .low(price)
                .close(price)
                .volume(price)
                .quoteVolume(price)
                .build();

        assertEquals(price, kline.getOpen());
        assertEquals(price, kline.getHigh());
        assertEquals(price, kline.getLow());
        assertEquals(price, kline.getClose());
        assertEquals(price, kline.getVolume());
        assertEquals(price, kline.getQuoteVolume());
    }
}

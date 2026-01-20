package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Symbol 实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("Symbol 实体测试")
class SymbolTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant now = Instant.now();
        
        Symbol symbol = Symbol.builder()
                .id(1L)
                .marketId(100L)
                .symbol("BTCUSDT")
                .baseAsset("BTC")
                .quoteAsset("USDT")
                .pricePrecision(8)
                .quantityPrecision(8)
                .realtimeSyncEnabled(false)
                .historySyncEnabled(false)
                .syncIntervals("1m,5m,1h")
                .status("TRADING")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, symbol.getId());
        assertEquals(100L, symbol.getMarketId());
        assertEquals("BTCUSDT", symbol.getSymbol());
        assertEquals("BTC", symbol.getBaseAsset());
        assertEquals("USDT", symbol.getQuoteAsset());
        assertEquals(8, symbol.getPricePrecision());
        assertEquals(8, symbol.getQuantityPrecision());
        assertFalse(symbol.getRealtimeSyncEnabled());
        assertFalse(symbol.getHistorySyncEnabled());
        assertEquals("1m,5m,1h", symbol.getSyncIntervals());
        assertEquals("TRADING", symbol.getStatus());
        assertEquals(now, symbol.getCreatedAt());
        assertEquals(now, symbol.getUpdatedAt());
    }

    @Test
    @DisplayName("无参构造函数创建实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        Symbol symbol = new Symbol();
        
        assertNull(symbol.getId());
        assertNull(symbol.getMarketId());
        assertNull(symbol.getSymbol());
        assertNull(symbol.getBaseAsset());
        assertNull(symbol.getQuoteAsset());
        assertNull(symbol.getPricePrecision());
        assertNull(symbol.getQuantityPrecision());
        assertNull(symbol.getRealtimeSyncEnabled());
        assertNull(symbol.getHistorySyncEnabled());
        assertNull(symbol.getSyncIntervals());
        assertNull(symbol.getStatus());
    }

    @Test
    @DisplayName("Setter 方法设置属性")
    void setter_shouldSetProperties() {
        Symbol symbol = new Symbol();
        
        symbol.setId(2L);
        symbol.setMarketId(200L);
        symbol.setSymbol("ETHUSDT");
        symbol.setBaseAsset("ETH");
        symbol.setQuoteAsset("USDT");
        symbol.setPricePrecision(6);
        symbol.setQuantityPrecision(4);
        symbol.setRealtimeSyncEnabled(true);
        symbol.setHistorySyncEnabled(true);
        symbol.setSyncIntervals("1m,15m,1d");
        symbol.setStatus("HALT");

        assertEquals(2L, symbol.getId());
        assertEquals(200L, symbol.getMarketId());
        assertEquals("ETHUSDT", symbol.getSymbol());
        assertEquals("ETH", symbol.getBaseAsset());
        assertEquals("USDT", symbol.getQuoteAsset());
        assertEquals(6, symbol.getPricePrecision());
        assertEquals(4, symbol.getQuantityPrecision());
        assertTrue(symbol.getRealtimeSyncEnabled());
        assertTrue(symbol.getHistorySyncEnabled());
        assertEquals("1m,15m,1d", symbol.getSyncIntervals());
        assertEquals("HALT", symbol.getStatus());
    }

    @Test
    @DisplayName("全参构造函数创建实体")
    void allArgsConstructor_shouldCreateEntity() {
        Instant now = Instant.now();
        
        Symbol symbol = new Symbol(
                3L, 300L, "BNBUSDT", "BNB", "USDT",
                8, 8, false, false, "1h,4h", "TRADING",
                now, now
        );

        assertEquals(3L, symbol.getId());
        assertEquals(300L, symbol.getMarketId());
        assertEquals("BNBUSDT", symbol.getSymbol());
        assertEquals("BNB", symbol.getBaseAsset());
        assertEquals("USDT", symbol.getQuoteAsset());
        assertEquals(8, symbol.getPricePrecision());
        assertEquals(8, symbol.getQuantityPrecision());
        assertFalse(symbol.getRealtimeSyncEnabled());
        assertFalse(symbol.getHistorySyncEnabled());
        assertEquals("1h,4h", symbol.getSyncIntervals());
        assertEquals("TRADING", symbol.getStatus());
    }

    @Test
    @DisplayName("同步开关默认应为关闭")
    void syncEnabled_shouldDefaultToFalse() {
        Symbol symbol = Symbol.builder()
                .symbol("BTCUSDT")
                .realtimeSyncEnabled(false)
                .historySyncEnabled(false)
                .build();

        assertFalse(symbol.getRealtimeSyncEnabled());
        assertFalse(symbol.getHistorySyncEnabled());
    }
}

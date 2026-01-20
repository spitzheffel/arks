package com.chanlun.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Market 实体测试
 * 
 * @author Chanlun Team
 */
@DisplayName("Market 实体测试")
class MarketTest {

    @Test
    @DisplayName("Builder 模式创建实体")
    void builder_shouldCreateEntity() {
        Instant now = Instant.now();
        
        Market market = Market.builder()
                .id(1L)
                .dataSourceId(100L)
                .name("现货市场")
                .marketType("SPOT")
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals(1L, market.getId());
        assertEquals(100L, market.getDataSourceId());
        assertEquals("现货市场", market.getName());
        assertEquals("SPOT", market.getMarketType());
        assertTrue(market.getEnabled());
        assertEquals(now, market.getCreatedAt());
        assertEquals(now, market.getUpdatedAt());
    }

    @Test
    @DisplayName("无参构造函数创建实体")
    void noArgsConstructor_shouldCreateEmptyEntity() {
        Market market = new Market();
        
        assertNull(market.getId());
        assertNull(market.getDataSourceId());
        assertNull(market.getName());
        assertNull(market.getMarketType());
        assertNull(market.getEnabled());
    }

    @Test
    @DisplayName("Setter 方法设置属性")
    void setter_shouldSetProperties() {
        Market market = new Market();
        
        market.setId(2L);
        market.setDataSourceId(200L);
        market.setName("U本位合约");
        market.setMarketType("USDT_M");
        market.setEnabled(false);

        assertEquals(2L, market.getId());
        assertEquals(200L, market.getDataSourceId());
        assertEquals("U本位合约", market.getName());
        assertEquals("USDT_M", market.getMarketType());
        assertFalse(market.getEnabled());
    }

    @Test
    @DisplayName("全参构造函数创建实体")
    void allArgsConstructor_shouldCreateEntity() {
        Instant now = Instant.now();
        
        Market market = new Market(3L, 300L, "币本位合约", "COIN_M", true, now, now);

        assertEquals(3L, market.getId());
        assertEquals(300L, market.getDataSourceId());
        assertEquals("币本位合约", market.getName());
        assertEquals("COIN_M", market.getMarketType());
        assertTrue(market.getEnabled());
    }
}

package com.chanlun.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarketType 枚举测试
 * 
 * @author Chanlun Team
 */
@DisplayName("MarketType 枚举测试")
class MarketTypeTest {

    @Test
    @DisplayName("SPOT 类型属性正确")
    void spot_shouldHaveCorrectProperties() {
        MarketType spot = MarketType.SPOT;
        
        assertEquals("SPOT", spot.getCode());
        assertEquals("现货", spot.getDescription());
    }

    @Test
    @DisplayName("USDT_M 类型属性正确")
    void usdtM_shouldHaveCorrectProperties() {
        MarketType usdtM = MarketType.USDT_M;
        
        assertEquals("USDT_M", usdtM.getCode());
        assertEquals("U本位合约", usdtM.getDescription());
    }

    @Test
    @DisplayName("COIN_M 类型属性正确")
    void coinM_shouldHaveCorrectProperties() {
        MarketType coinM = MarketType.COIN_M;
        
        assertEquals("COIN_M", coinM.getCode());
        assertEquals("币本位合约", coinM.getDescription());
    }

    @Test
    @DisplayName("fromCode 正确解析大写代码")
    void fromCode_shouldParseUpperCase() {
        assertEquals(MarketType.SPOT, MarketType.fromCode("SPOT"));
        assertEquals(MarketType.USDT_M, MarketType.fromCode("USDT_M"));
        assertEquals(MarketType.COIN_M, MarketType.fromCode("COIN_M"));
    }

    @Test
    @DisplayName("fromCode 正确解析小写代码")
    void fromCode_shouldParseLowerCase() {
        assertEquals(MarketType.SPOT, MarketType.fromCode("spot"));
        assertEquals(MarketType.USDT_M, MarketType.fromCode("usdt_m"));
        assertEquals(MarketType.COIN_M, MarketType.fromCode("coin_m"));
    }

    @Test
    @DisplayName("fromCode 未知代码应抛出异常")
    void fromCode_unknownCode_shouldThrowException() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MarketType.fromCode("UNKNOWN")
        );
        
        assertTrue(exception.getMessage().contains("Unknown market type"));
    }

    @Test
    @DisplayName("枚举值数量正确")
    void values_shouldHaveThreeTypes() {
        assertEquals(3, MarketType.values().length);
    }
}

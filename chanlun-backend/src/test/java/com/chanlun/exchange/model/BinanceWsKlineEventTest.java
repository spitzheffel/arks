package com.chanlun.exchange.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BinanceWsKlineEvent 单元测试
 * 
 * @author Chanlun Team
 */
@DisplayName("BinanceWsKlineEvent 测试")
class BinanceWsKlineEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("测试 JSON 反序列化")
    void testJsonDeserialization() throws Exception {
        String json = """
                {
                  "e": "kline",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "k": {
                    "t": 1672515780000,
                    "T": 1672515839999,
                    "s": "BTCUSDT",
                    "i": "1m",
                    "f": 100,
                    "L": 200,
                    "o": "50000.00",
                    "c": "50100.00",
                    "h": "50200.00",
                    "l": "49900.00",
                    "v": "100.5",
                    "n": 150,
                    "x": true,
                    "q": "5050000.00",
                    "V": "50.25",
                    "Q": "2525000.00"
                  }
                }
                """;

        BinanceWsKlineEvent event = objectMapper.readValue(json, BinanceWsKlineEvent.class);

        assertEquals("kline", event.getEventType());
        assertEquals(1672515782136L, event.getEventTime());
        assertEquals("BTCUSDT", event.getSymbol());
        
        BinanceWsKlineEvent.KlineData kline = event.getKline();
        assertNotNull(kline);
        assertEquals(1672515780000L, kline.getOpenTime());
        assertEquals(1672515839999L, kline.getCloseTime());
        assertEquals("BTCUSDT", kline.getSymbol());
        assertEquals("1m", kline.getInterval());
        assertEquals(new BigDecimal("50000.00"), kline.getOpen());
        assertEquals(new BigDecimal("50100.00"), kline.getClose());
        assertEquals(new BigDecimal("50200.00"), kline.getHigh());
        assertEquals(new BigDecimal("49900.00"), kline.getLow());
        assertEquals(new BigDecimal("100.5"), kline.getVolume());
        assertEquals(150, kline.getTrades());
        assertTrue(kline.getClosed());
        assertEquals(new BigDecimal("5050000.00"), kline.getQuoteVolume());
    }

    @Test
    @DisplayName("测试 isKlineEvent 方法")
    void testIsKlineEvent() {
        BinanceWsKlineEvent event = BinanceWsKlineEvent.builder()
                .eventType("kline")
                .build();
        
        assertTrue(event.isKlineEvent());
        
        event.setEventType("trade");
        assertFalse(event.isKlineEvent());
        
        event.setEventType(null);
        assertFalse(event.isKlineEvent());
    }

    @Test
    @DisplayName("测试 isKlineClosed 方法")
    void testIsKlineClosed() {
        BinanceWsKlineEvent.KlineData kline = BinanceWsKlineEvent.KlineData.builder()
                .closed(true)
                .build();
        
        BinanceWsKlineEvent event = BinanceWsKlineEvent.builder()
                .kline(kline)
                .build();
        
        assertTrue(event.isKlineClosed());
        
        kline.setClosed(false);
        assertFalse(event.isKlineClosed());
        
        kline.setClosed(null);
        assertFalse(event.isKlineClosed());
        
        event.setKline(null);
        assertFalse(event.isKlineClosed());
    }

    @Test
    @DisplayName("测试 getEventTimeInstant 方法")
    void testGetEventTimeInstant() {
        BinanceWsKlineEvent event = BinanceWsKlineEvent.builder()
                .eventTime(1672515782136L)
                .build();
        
        Instant instant = event.getEventTimeInstant();
        
        assertNotNull(instant);
        assertEquals(1672515782136L, instant.toEpochMilli());
        
        event.setEventTime(null);
        assertNull(event.getEventTimeInstant());
    }

    @Test
    @DisplayName("测试 KlineData getOpenTimeInstant 方法")
    void testKlineDataGetOpenTimeInstant() {
        BinanceWsKlineEvent.KlineData kline = BinanceWsKlineEvent.KlineData.builder()
                .openTime(1672515780000L)
                .build();
        
        Instant instant = kline.getOpenTimeInstant();
        
        assertNotNull(instant);
        assertEquals(1672515780000L, instant.toEpochMilli());
        
        kline.setOpenTime(null);
        assertNull(kline.getOpenTimeInstant());
    }

    @Test
    @DisplayName("测试 KlineData getCloseTimeInstant 方法")
    void testKlineDataGetCloseTimeInstant() {
        BinanceWsKlineEvent.KlineData kline = BinanceWsKlineEvent.KlineData.builder()
                .closeTime(1672515839999L)
                .build();
        
        Instant instant = kline.getCloseTimeInstant();
        
        assertNotNull(instant);
        assertEquals(1672515839999L, instant.toEpochMilli());
        
        kline.setCloseTime(null);
        assertNull(kline.getCloseTimeInstant());
    }

    @Test
    @DisplayName("测试 KlineData toBinanceKline 方法")
    void testKlineDataToBinanceKline() {
        BinanceWsKlineEvent.KlineData kline = BinanceWsKlineEvent.KlineData.builder()
                .openTime(1672515780000L)
                .closeTime(1672515839999L)
                .open(new BigDecimal("50000.00"))
                .high(new BigDecimal("50200.00"))
                .low(new BigDecimal("49900.00"))
                .close(new BigDecimal("50100.00"))
                .volume(new BigDecimal("100.5"))
                .quoteVolume(new BigDecimal("5050000.00"))
                .trades(150)
                .takerBuyBaseVolume(new BigDecimal("50.25"))
                .takerBuyQuoteVolume(new BigDecimal("2525000.00"))
                .build();
        
        BinanceKline binanceKline = kline.toBinanceKline();
        
        assertNotNull(binanceKline);
        assertEquals(1672515780000L, binanceKline.getOpenTime());
        assertEquals(1672515839999L, binanceKline.getCloseTime());
        assertEquals(new BigDecimal("50000.00"), binanceKline.getOpen());
        assertEquals(new BigDecimal("50200.00"), binanceKline.getHigh());
        assertEquals(new BigDecimal("49900.00"), binanceKline.getLow());
        assertEquals(new BigDecimal("50100.00"), binanceKline.getClose());
        assertEquals(new BigDecimal("100.5"), binanceKline.getVolume());
        assertEquals(new BigDecimal("5050000.00"), binanceKline.getQuoteVolume());
        assertEquals(150, binanceKline.getTrades());
        assertEquals(new BigDecimal("50.25"), binanceKline.getTakerBuyBaseVolume());
        assertEquals(new BigDecimal("2525000.00"), binanceKline.getTakerBuyQuoteVolume());
    }

    @Test
    @DisplayName("测试未完结 K 线")
    void testUnfinishedKline() throws Exception {
        String json = """
                {
                  "e": "kline",
                  "E": 1672515782136,
                  "s": "BTCUSDT",
                  "k": {
                    "t": 1672515780000,
                    "T": 1672515839999,
                    "s": "BTCUSDT",
                    "i": "1m",
                    "o": "50000.00",
                    "c": "50050.00",
                    "h": "50100.00",
                    "l": "49950.00",
                    "v": "50.0",
                    "n": 75,
                    "x": false,
                    "q": "2500000.00"
                  }
                }
                """;

        BinanceWsKlineEvent event = objectMapper.readValue(json, BinanceWsKlineEvent.class);

        assertTrue(event.isKlineEvent());
        assertFalse(event.isKlineClosed());
        assertFalse(event.getKline().getClosed());
    }
}

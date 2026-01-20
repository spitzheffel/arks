package com.chanlun.exchange.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 币安 WebSocket K线事件模型
 * 
 * 对应币安 WebSocket 推送的 K 线数据结构
 * 
 * WebSocket K线推送结构:
 * {
 *   "e": "kline",
 *   "E": 1672515782136,
 *   "s": "BTCUSDT",
 *   "k": {
 *     "t": 1672515780000,   // K线开始时间
 *     "T": 1672515839999,   // K线结束时间
 *     "s": "BTCUSDT",       // 交易对
 *     "i": "1m",            // 周期
 *     "f": 100,             // 第一笔成交ID
 *     "L": 200,             // 最后一笔成交ID
 *     "o": "0.0010",        // 开盘价
 *     "c": "0.0020",        // 收盘价
 *     "h": "0.0025",        // 最高价
 *     "l": "0.0015",        // 最低价
 *     "v": "1000",          // 成交量
 *     "n": 100,             // 成交笔数
 *     "x": false,           // K线是否完结
 *     "q": "1.0000",        // 成交额
 *     "V": "500",           // 主动买入成交量
 *     "Q": "0.500"          // 主动买入成交额
 *   }
 * }
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceWsKlineEvent {

    /**
     * 事件类型 (kline)
     */
    @JsonProperty("e")
    private String eventType;

    /**
     * 事件时间 (UTC 毫秒)
     */
    @JsonProperty("E")
    private Long eventTime;

    /**
     * 交易对
     */
    @JsonProperty("s")
    private String symbol;

    /**
     * K线数据
     */
    @JsonProperty("k")
    private KlineData kline;

    /**
     * K线数据内部结构
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlineData {

        /**
         * K线开始时间 (UTC 毫秒)
         */
        @JsonProperty("t")
        private Long openTime;

        /**
         * K线结束时间 (UTC 毫秒)
         */
        @JsonProperty("T")
        private Long closeTime;

        /**
         * 交易对
         */
        @JsonProperty("s")
        private String symbol;

        /**
         * 时间周期
         */
        @JsonProperty("i")
        private String interval;

        /**
         * 第一笔成交ID
         */
        @JsonProperty("f")
        private Long firstTradeId;

        /**
         * 最后一笔成交ID
         */
        @JsonProperty("L")
        private Long lastTradeId;

        /**
         * 开盘价
         */
        @JsonProperty("o")
        private BigDecimal open;

        /**
         * 收盘价
         */
        @JsonProperty("c")
        private BigDecimal close;

        /**
         * 最高价
         */
        @JsonProperty("h")
        private BigDecimal high;

        /**
         * 最低价
         */
        @JsonProperty("l")
        private BigDecimal low;

        /**
         * 成交量
         */
        @JsonProperty("v")
        private BigDecimal volume;

        /**
         * 成交笔数
         */
        @JsonProperty("n")
        private Integer trades;

        /**
         * K线是否完结
         */
        @JsonProperty("x")
        private Boolean closed;

        /**
         * 成交额
         */
        @JsonProperty("q")
        private BigDecimal quoteVolume;

        /**
         * 主动买入成交量
         */
        @JsonProperty("V")
        private BigDecimal takerBuyBaseVolume;

        /**
         * 主动买入成交额
         */
        @JsonProperty("Q")
        private BigDecimal takerBuyQuoteVolume;

        /**
         * 获取开盘时间 (Instant)
         */
        public Instant getOpenTimeInstant() {
            return openTime != null ? Instant.ofEpochMilli(openTime) : null;
        }

        /**
         * 获取收盘时间 (Instant)
         */
        public Instant getCloseTimeInstant() {
            return closeTime != null ? Instant.ofEpochMilli(closeTime) : null;
        }

        /**
         * 转换为 BinanceKline 对象
         */
        public BinanceKline toBinanceKline() {
            return BinanceKline.builder()
                    .openTime(openTime)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .closeTime(closeTime)
                    .quoteVolume(quoteVolume)
                    .trades(trades)
                    .takerBuyBaseVolume(takerBuyBaseVolume)
                    .takerBuyQuoteVolume(takerBuyQuoteVolume)
                    .build();
        }
    }

    /**
     * 获取事件时间 (Instant)
     */
    public Instant getEventTimeInstant() {
        return eventTime != null ? Instant.ofEpochMilli(eventTime) : null;
    }

    /**
     * 检查是否为 K 线事件
     */
    public boolean isKlineEvent() {
        return "kline".equals(eventType);
    }

    /**
     * 检查 K 线是否完结
     */
    public boolean isKlineClosed() {
        return kline != null && Boolean.TRUE.equals(kline.getClosed());
    }
}

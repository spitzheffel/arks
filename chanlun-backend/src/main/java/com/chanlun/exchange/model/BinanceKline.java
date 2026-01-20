package com.chanlun.exchange.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 币安 K 线数据模型
 * 
 * 对应币安 API 返回的 K 线数据结构
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceKline {

    /**
     * 开盘时间 (UTC 毫秒)
     */
    private Long openTime;

    /**
     * 开盘价
     */
    private BigDecimal open;

    /**
     * 最高价
     */
    private BigDecimal high;

    /**
     * 最低价
     */
    private BigDecimal low;

    /**
     * 收盘价
     */
    private BigDecimal close;

    /**
     * 成交量
     */
    private BigDecimal volume;

    /**
     * 收盘时间 (UTC 毫秒)
     */
    private Long closeTime;

    /**
     * 成交额
     */
    private BigDecimal quoteVolume;

    /**
     * 成交笔数
     */
    private Integer trades;

    /**
     * 主动买入成交量
     */
    private BigDecimal takerBuyBaseVolume;

    /**
     * 主动买入成交额
     */
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
     * 从币安 API 返回的数组解析 K 线数据
     * 
     * 币安 K 线数据格式:
     * [
     *   1499040000000,      // 0: 开盘时间 (毫秒)
     *   "0.01634790",       // 1: 开盘价
     *   "0.80000000",       // 2: 最高价
     *   "0.01575800",       // 3: 最低价
     *   "0.01577100",       // 4: 收盘价
     *   "148976.11427815",  // 5: 成交量
     *   1499644799999,      // 6: 收盘时间
     *   "2434.19055334",    // 7: 成交额
     *   308,                // 8: 成交笔数
     *   "1756.87402397",    // 9: 主动买入成交量
     *   "28.46694368",      // 10: 主动买入成交额
     *   "0"                 // 11: 忽略
     * ]
     * 
     * @param data 币安 API 返回的数组
     * @return BinanceKline 对象
     */
    public static BinanceKline fromArray(Object[] data) {
        if (data == null || data.length < 11) {
            throw new IllegalArgumentException("Invalid kline data array");
        }

        return BinanceKline.builder()
                .openTime(parseLong(data[0]))
                .open(parseBigDecimal(data[1]))
                .high(parseBigDecimal(data[2]))
                .low(parseBigDecimal(data[3]))
                .close(parseBigDecimal(data[4]))
                .volume(parseBigDecimal(data[5]))
                .closeTime(parseLong(data[6]))
                .quoteVolume(parseBigDecimal(data[7]))
                .trades(parseInteger(data[8]))
                .takerBuyBaseVolume(parseBigDecimal(data[9]))
                .takerBuyQuoteVolume(parseBigDecimal(data[10]))
                .build();
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.parseInt(value.toString());
    }
}

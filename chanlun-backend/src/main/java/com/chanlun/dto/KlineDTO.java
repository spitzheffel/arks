package com.chanlun.dto;

import com.chanlun.entity.Kline;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * K线数据传输对象
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDTO {

    /**
     * K线ID
     */
    private Long id;

    /**
     * 交易对ID
     */
    private Long symbolId;

    /**
     * 时间周期 (1m/3m/5m/15m/30m/1h/2h/4h/6h/8h/12h/1d/3d/1w/1M)
     */
    private String interval;

    /**
     * 开盘时间 (UTC, ISO 8601)
     */
    private Instant openTime;

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
     * 成交额
     */
    private BigDecimal quoteVolume;

    /**
     * 成交笔数
     */
    private Integer trades;

    /**
     * 收盘时间 (UTC, ISO 8601)
     */
    private Instant closeTime;

    /**
     * 从实体转换为 DTO
     */
    public static KlineDTO fromEntity(Kline kline) {
        if (kline == null) {
            return null;
        }
        return KlineDTO.builder()
                .id(kline.getId())
                .symbolId(kline.getSymbolId())
                .interval(kline.getInterval())
                .openTime(kline.getOpenTime())
                .open(kline.getOpen())
                .high(kline.getHigh())
                .low(kline.getLow())
                .close(kline.getClose())
                .volume(kline.getVolume())
                .quoteVolume(kline.getQuoteVolume())
                .trades(kline.getTrades())
                .closeTime(kline.getCloseTime())
                .build();
    }
}

package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * K线数据实体
 * 
 * 存储各交易对各周期的 OHLCV 数据
 * 唯一约束: (symbol_id, interval, open_time)
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("kline")
public class Kline {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 交易对ID (外键)
     */
    private Long symbolId;

    /**
     * 时间周期 (1m/3m/5m/15m/30m/1h/2h/4h/6h/8h/12h/1d/3d/1w/1M)
     */
    @TableField("`interval`")
    private String interval;

    /**
     * 开盘时间 (UTC)
     */
    private Instant openTime;

    /**
     * 开盘价
     */
    @TableField("`open`")
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
    @TableField("`close`")
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
     * 收盘时间 (UTC)
     */
    private Instant closeTime;

    /**
     * 创建时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
}

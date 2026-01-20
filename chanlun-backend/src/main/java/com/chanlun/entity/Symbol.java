package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 交易对实体
 * 
 * 存储交易对基本信息和同步配置
 * 每个交易对关联到特定的市场
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("symbol")
public class Symbol {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 市场ID (外键)
     */
    private Long marketId;

    /**
     * 交易对代码 (如 BTCUSDT)
     */
    private String symbol;

    /**
     * 基础货币 (如 BTC)
     */
    private String baseAsset;

    /**
     * 报价货币 (如 USDT)
     */
    private String quoteAsset;

    /**
     * 价格精度
     */
    private Integer pricePrecision;

    /**
     * 数量精度
     */
    private Integer quantityPrecision;

    /**
     * 实时同步开关 (默认关闭)
     */
    private Boolean realtimeSyncEnabled;

    /**
     * 历史同步开关 (默认关闭)
     */
    private Boolean historySyncEnabled;

    /**
     * 同步周期 (逗号分隔: 1m,5m,1h)
     */
    private String syncIntervals;

    /**
     * 交易对状态 (TRADING/HALT)
     */
    private String status;

    /**
     * 创建时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}

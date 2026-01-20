package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据缺口 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataGapDTO {

    /**
     * 缺口ID
     */
    private Long id;

    /**
     * 交易对ID
     */
    private Long symbolId;

    /**
     * 交易对代码
     */
    private String symbol;

    /**
     * 市场ID
     */
    private Long marketId;

    /**
     * 市场名称
     */
    private String marketName;

    /**
     * 数据源ID
     */
    private Long dataSourceId;

    /**
     * 数据源名称
     */
    private String dataSourceName;

    /**
     * 时间周期
     */
    private String interval;

    /**
     * 缺口开始时间 (UTC)
     */
    private Instant gapStart;

    /**
     * 缺口结束时间 (UTC)
     */
    private Instant gapEnd;

    /**
     * 缺失K线数量
     */
    private Integer missingCount;

    /**
     * 状态 (PENDING/FILLING/FILLED/FAILED)
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间 (UTC)
     */
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    private Instant updatedAt;
}

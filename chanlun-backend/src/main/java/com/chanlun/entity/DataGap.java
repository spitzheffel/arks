package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 数据缺口实体
 * 
 * 记录 K 线数据中检测到的缺口信息
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("data_gap")
public class DataGap {

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
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}

package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 同步状态实体
 * 
 * 记录每个交易对每个周期的同步状态信息
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sync_status")
public class SyncStatus {

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
     * 最后同步时间 (UTC)
     */
    private Instant lastSyncTime;

    /**
     * 最后一根K线的开盘时间 (UTC)，无数据时为 NULL
     */
    private Instant lastKlineTime;

    /**
     * 总K线数量
     */
    private Long totalKlines;

    /**
     * 该周期自动回补开关 (默认 true)
     */
    private Boolean autoGapFillEnabled;

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

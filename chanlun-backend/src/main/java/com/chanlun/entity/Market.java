package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 市场实体
 * 
 * 存储不同市场类型信息（现货、U本位合约、币本位合约）
 * 每个市场关联到特定的数据源
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("market")
public class Market {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源ID (外键)
     */
    private Long dataSourceId;

    /**
     * 市场名称
     */
    private String name;

    /**
     * 市场类型 (SPOT/USDT_M/COIN_M)
     */
    private String marketType;

    /**
     * 是否启用
     */
    private Boolean enabled;

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

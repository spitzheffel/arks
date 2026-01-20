package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * K线删除结果
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineDeleteResult {

    /**
     * 交易对ID
     */
    private Long symbolId;

    /**
     * 时间周期
     */
    private String interval;

    /**
     * 删除的开始时间
     */
    private Instant startTime;

    /**
     * 删除的结束时间
     */
    private Instant endTime;

    /**
     * 删除的记录数
     */
    private Integer deletedCount;
}

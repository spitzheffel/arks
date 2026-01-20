package com.chanlun.dto;

import com.chanlun.entity.SyncStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 同步状态传输对象
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatusDTO {

    /**
     * 同步状态ID
     */
    private Long id;

    /**
     * 交易对ID
     */
    private Long symbolId;

    /**
     * 时间周期
     */
    private String interval;

    /**
     * 最后同步时间 (UTC)
     */
    private Instant lastSyncTime;

    /**
     * 最后一根K线的开盘时间 (UTC)，无数据时为 null
     */
    private Instant lastKlineTime;

    /**
     * 总K线数量
     */
    private Long totalKlines;

    /**
     * 该周期自动回补开关
     */
    private Boolean autoGapFillEnabled;

    /**
     * 创建时间 (UTC)
     */
    private Instant createdAt;

    /**
     * 更新时间 (UTC)
     */
    private Instant updatedAt;

    /**
     * 从实体转换为 DTO
     */
    public static SyncStatusDTO fromEntity(SyncStatus status) {
        if (status == null) {
            return null;
        }
        return SyncStatusDTO.builder()
                .id(status.getId())
                .symbolId(status.getSymbolId())
                .interval(status.getInterval())
                .lastSyncTime(status.getLastSyncTime())
                .lastKlineTime(status.getLastKlineTime())
                .totalKlines(status.getTotalKlines())
                .autoGapFillEnabled(status.getAutoGapFillEnabled())
                .createdAt(status.getCreatedAt())
                .updatedAt(status.getUpdatedAt())
                .build();
    }
}

package com.chanlun.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 历史同步结果 DTO
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorySyncResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 同步任务 ID
     */
    private Long taskId;

    /**
     * 交易对 ID
     */
    private Long symbolId;

    /**
     * 时间周期
     */
    private String interval;

    /**
     * 开始时间
     */
    private Instant startTime;

    /**
     * 结束时间
     */
    private Instant endTime;

    /**
     * 同步的 K 线数量
     */
    private Integer syncedCount;

    /**
     * 同步耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建成功结果
     */
    public static HistorySyncResult success(Long taskId, Long symbolId, String interval,
                                             Instant startTime, Instant endTime,
                                             int syncedCount, long durationMs) {
        return HistorySyncResult.builder()
                .success(true)
                .message("历史数据同步成功")
                .taskId(taskId)
                .symbolId(symbolId)
                .interval(interval)
                .startTime(startTime)
                .endTime(endTime)
                .syncedCount(syncedCount)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static HistorySyncResult failure(Long symbolId, String interval,
                                             Instant startTime, Instant endTime,
                                             String errorMessage) {
        return HistorySyncResult.builder()
                .success(false)
                .message(errorMessage)
                .symbolId(symbolId)
                .interval(interval)
                .startTime(startTime)
                .endTime(endTime)
                .build();
    }
}

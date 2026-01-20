package com.chanlun.dto;

import com.chanlun.entity.SyncTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 同步任务传输对象
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncTaskDTO {

    /**
     * 任务ID
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
     * 任务类型 (REALTIME/HISTORY/GAP_FILL)
     */
    private String taskType;

    /**
     * 状态 (PENDING/RUNNING/SUCCESS/FAILED)
     */
    private String status;

    /**
     * 同步起始时间 (UTC)
     */
    private Instant startTime;

    /**
     * 同步结束时间 (UTC)
     */
    private Instant endTime;

    /**
     * 已同步数量
     */
    private Integer syncedCount;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetries;

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

    /**
     * 从实体转换为 DTO
     */
    public static SyncTaskDTO fromEntity(SyncTask task) {
        if (task == null) {
            return null;
        }
        return SyncTaskDTO.builder()
                .id(task.getId())
                .symbolId(task.getSymbolId())
                .interval(task.getInterval())
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .startTime(task.getStartTime())
                .endTime(task.getEndTime())
                .syncedCount(task.getSyncedCount())
                .retryCount(task.getRetryCount())
                .maxRetries(task.getMaxRetries())
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}

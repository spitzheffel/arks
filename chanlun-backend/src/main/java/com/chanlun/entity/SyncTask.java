package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 同步任务实体
 * 
 * 记录实时/历史/缺口回补任务执行记录
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sync_task")
public class SyncTask {

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
     * 最大重试次数 (默认3)
     */
    private Integer maxRetries;

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

    /**
     * 任务类型枚举
     */
    public static final class TaskType {
        public static final String REALTIME = "REALTIME";
        public static final String HISTORY = "HISTORY";
        public static final String GAP_FILL = "GAP_FILL";
        
        private TaskType() {}
    }

    /**
     * 任务状态枚举
     */
    public static final class Status {
        public static final String PENDING = "PENDING";
        public static final String RUNNING = "RUNNING";
        public static final String SUCCESS = "SUCCESS";
        public static final String FAILED = "FAILED";
        
        private Status() {}
    }
}

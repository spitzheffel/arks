package com.chanlun.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 系统配置实体
 * 
 * 存储系统级配置项
 * 
 * @author Chanlun Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("system_config")
public class SystemConfig {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置说明
     */
    private String description;

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
     * 预置配置键常量
     */
    public static final class Keys {
        /** 交易对同步 Cron 表达式 */
        public static final String SYNC_SYMBOL_CRON = "sync.symbol.cron";
        /** 实时同步总开关 */
        public static final String SYNC_REALTIME_ENABLED = "sync.realtime.enabled";
        /** 历史数据增量同步 Cron 表达式 */
        public static final String SYNC_HISTORY_CRON = "sync.history.cron";
        /** 历史数据自动增量同步开关 */
        public static final String SYNC_HISTORY_AUTO = "sync.history.auto";
        /** 缺口检测 Cron 表达式 */
        public static final String SYNC_GAP_DETECT_CRON = "sync.gap_detect.cron";
        /** 自动缺口回补开关 */
        public static final String SYNC_GAP_FILL_AUTO = "sync.gap_fill.auto";
        /** 回补最大重试次数 */
        public static final String SYNC_GAP_FILL_MAX_RETRY = "sync.gap_fill.max_retry";
        /** 单次回补批量大小 */
        public static final String SYNC_GAP_FILL_BATCH_SIZE = "sync.gap_fill.batch_size";
        /** 回补任务间隔毫秒数 */
        public static final String SYNC_GAP_FILL_INTERVAL_MS = "sync.gap_fill.interval_ms";

        private Keys() {}
    }

    /**
     * 默认配置值常量
     */
    public static final class Defaults {
        /** 交易对同步 Cron 表达式默认值 (每天凌晨2点 UTC) */
        public static final String SYNC_SYMBOL_CRON = "0 0 2 * * ?";
        /** 实时同步总开关默认值 */
        public static final boolean SYNC_REALTIME_ENABLED = true;
        /** 历史数据增量同步 Cron 表达式默认值 (每天凌晨3:30 UTC) */
        public static final String SYNC_HISTORY_CRON = "0 30 3 * * ?";
        /** 历史数据自动增量同步开关默认值 */
        public static final boolean SYNC_HISTORY_AUTO = true;
        /** 缺口检测 Cron 表达式默认值 (每小时整点) */
        public static final String SYNC_GAP_DETECT_CRON = "0 0 * * * ?";
        /** 自动缺口回补开关默认值 */
        public static final boolean SYNC_GAP_FILL_AUTO = false;
        /** 回补最大重试次数默认值 */
        public static final int SYNC_GAP_FILL_MAX_RETRY = 3;
        /** 单次回补批量大小默认值 */
        public static final int SYNC_GAP_FILL_BATCH_SIZE = 10;
        /** 回补任务间隔毫秒数默认值 */
        public static final long SYNC_GAP_FILL_INTERVAL_MS = 1000;

        private Defaults() {}
    }
}

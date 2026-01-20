package com.chanlun.scheduler;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.service.HistorySyncService;
import com.chanlun.service.SyncFilterService;
import com.chanlun.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史数据同步定时任务
 * 
 * 定时执行历史数据增量同步，检查 sync.history.auto 开关
 * 所有定时任务按 UTC 时区执行
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistorySyncScheduler {

    private final HistorySyncService historySyncService;
    private final SystemConfigService systemConfigService;
    private final SyncFilterService syncFilterService;

    /**
     * 历史数据增量同步定时任务
     * 
     * 默认每天凌晨 3:30 UTC 执行
     * 可通过 system_config 表的 sync.history.cron 配置
     * 
     * 执行前检查 sync.history.auto 开关，如果关闭则跳过
     * 
     * 同步逻辑：
     * 1. 检查 sync.history.auto 开关
     * 2. 获取所有符合条件的交易对（使用 SyncFilterService）
     * 3. 对每个交易对的每个配置周期执行增量同步
     */
    @Scheduled(cron = "${app.sync.history.cron:0 30 3 * * ?}", zone = "UTC")
    public void executeHistorySync() {
        log.info("History sync scheduler triggered");
        
        // 检查自动同步开关
        if (!systemConfigService.isHistoryAutoSyncEnabled()) {
            log.info("History auto sync is disabled, skipping");
            return;
        }
        
        try {
            log.info("Starting scheduled history incremental sync");
            HistorySyncService.IncrementalSyncSummary summary = historySyncService.syncAllIncremental();
            
            log.info("Scheduled history sync completed: {} symbols, {} succeeded, {} failed, {} klines",
                    summary.getTotalSymbols(),
                    summary.getSuccessCount(),
                    summary.getFailureCount(),
                    summary.getTotalKlines());
            
        } catch (Exception e) {
            log.error("Scheduled history sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发历史数据增量同步
     * 
     * 不检查 sync.history.auto 开关，直接执行同步
     * 
     * @return 同步结果摘要
     */
    public HistorySyncService.IncrementalSyncSummary triggerManualSync() {
        log.info("Manual history sync triggered");
        return historySyncService.syncAllIncremental();
    }

    /**
     * 获取待同步的交易对数量
     * 
     * @return 符合同步条件的交易对数量
     */
    public int getPendingSyncCount() {
        List<SymbolDTO> targets = syncFilterService.getHistorySyncTargets();
        return targets.size();
    }
}

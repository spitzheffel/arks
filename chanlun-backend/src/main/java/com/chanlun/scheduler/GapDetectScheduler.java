package com.chanlun.scheduler;

import com.chanlun.dto.GapDetectResult;
import com.chanlun.service.DataGapService;
import com.chanlun.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据缺口检测定时任务
 * 
 * 定时执行数据缺口检测，检测所有符合条件的交易对的数据缺口
 * 所有定时任务按 UTC 时区执行
 * 
 * 筛选条件（由 DataGapService.detectAllGaps 实现）：
 * - 数据源已启用且未删除
 * - 市场已启用
 * - 交易对 history_sync_enabled = true
 * - 交易对配置了 sync_intervals
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GapDetectScheduler {

    private final DataGapService dataGapService;
    private final SystemConfigService systemConfigService;

    /**
     * 数据缺口检测定时任务
     * 
     * 默认每小时整点执行
     * 可通过 system_config 表的 sync.gap_detect.cron 配置
     * 
     * 执行逻辑：
     * 1. 获取所有符合条件的交易对
     * 2. 对每个交易对的每个配置周期执行缺口检测
     * 3. 将检测到的新缺口保存到数据库
     */
    @Scheduled(cron = "${app.sync.gap-detect.cron:0 0 * * * ?}", zone = "UTC")
    public void executeGapDetect() {
        log.info("Gap detect scheduler triggered");
        
        try {
            log.info("Starting scheduled gap detection");
            GapDetectResult result = dataGapService.detectAllGaps();
            
            log.info("Scheduled gap detection completed: {} symbols, {} intervals, {} new gaps, {} total gaps",
                    result.getSymbolCount(),
                    result.getIntervalCount(),
                    result.getNewGapCount(),
                    result.getTotalGapCount());
            
        } catch (Exception e) {
            log.error("Scheduled gap detection failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发缺口检测
     * 
     * @return 检测结果
     */
    public GapDetectResult triggerManualDetect() {
        log.info("Manual gap detection triggered");
        return dataGapService.detectAllGaps();
    }

    /**
     * 获取缺口检测 Cron 表达式
     * 
     * @return Cron 表达式
     */
    public String getGapDetectCron() {
        return systemConfigService.getGapDetectCron();
    }

    /**
     * 获取待处理的缺口数量
     * 
     * @return 待处理缺口数量
     */
    public long getPendingGapCount() {
        return dataGapService.countPending();
    }
}

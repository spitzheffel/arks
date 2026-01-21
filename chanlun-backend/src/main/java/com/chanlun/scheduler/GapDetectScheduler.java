package com.chanlun.scheduler;

import com.chanlun.dto.GapDetectResult;
import com.chanlun.service.DataGapService;
import com.chanlun.service.GapFillService;
import com.chanlun.service.GapFillService.BatchGapFillResult;
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
    private final GapFillService gapFillService;
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

    /**
     * 自动缺口回补定时任务
     * 
     * 在缺口检测后执行，自动回补符合条件的缺口
     * 需要全局开关 sync.gap_fill.auto = true 才会执行
     * 
     * 执行逻辑：
     * 1. 检查全局自动回补开关
     * 2. 获取待回补的缺口（按批量大小限制）
     * 3. 检查每个缺口的周期级自动回补开关
     * 4. 执行回补并更新状态
     */
    @Scheduled(cron = "${app.sync.gap-fill.cron:0 5 * * * ?}", zone = "UTC")
    public void executeAutoGapFill() {
        log.info("Auto gap fill scheduler triggered");
        
        try {
            // 检查全局开关
            if (!systemConfigService.isAutoGapFillEnabled()) {
                log.debug("Auto gap fill is disabled globally, skipping");
                return;
            }
            
            log.info("Starting scheduled auto gap fill");
            BatchGapFillResult result = gapFillService.autoFillGaps();
            
            if (result.isDisabled()) {
                log.info("Auto gap fill skipped: {}", result.getMessage());
                return;
            }
            
            log.info("Scheduled auto gap fill completed: total={}, success={}, failed={}, skipped={}, klines={}",
                    result.getTotalCount(),
                    result.getSuccessCount(),
                    result.getFailureCount(),
                    result.getSkippedCount(),
                    result.getTotalSyncedKlines());
            
        } catch (Exception e) {
            log.error("Scheduled auto gap fill failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发自动缺口回补
     * 
     * @return 回补结果
     */
    public BatchGapFillResult triggerManualAutoFill() {
        log.info("Manual auto gap fill triggered");
        return gapFillService.autoFillGaps();
    }

    /**
     * 检查自动缺口回补是否启用
     * 
     * @return 是否启用
     */
    public boolean isAutoGapFillEnabled() {
        return systemConfigService.isAutoGapFillEnabled();
    }
}

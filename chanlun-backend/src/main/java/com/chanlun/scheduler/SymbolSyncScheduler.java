package com.chanlun.scheduler;

import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.SymbolSyncResult;
import com.chanlun.service.DataSourceService;
import com.chanlun.service.MarketService;
import com.chanlun.service.SymbolService;
import com.chanlun.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 交易对列表定时同步任务
 * 
 * 定时从交易所同步交易对列表，更新本地数据库
 * 默认每天凌晨 2:00 UTC 执行
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SymbolSyncScheduler {

    private final SymbolService symbolService;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final SystemConfigService systemConfigService;

    /**
     * 交易对列表定时同步任务
     * 
     * 默认每天凌晨 2:00 UTC 执行
     * 可通过 system_config 表的 sync.symbol.cron 配置
     * 
     * 同步逻辑：
     * 1. 获取所有启用的数据源
     * 2. 对每个数据源，获取其启用的市场
     * 3. 对每个市场，从交易所同步交易对列表
     */
    @Scheduled(cron = "${app.sync.symbol.cron:0 0 2 * * ?}", zone = "UTC")
    public void executeSymbolSync() {
        log.info("Symbol sync scheduler triggered");
        
        try {
            SymbolSyncSummary summary = syncAllSymbols();
            
            log.info("Scheduled symbol sync completed: {} markets processed, {} symbols synced, " +
                    "{} created, {} updated, {} failures",
                    summary.marketsProcessed,
                    summary.totalSymbols,
                    summary.createdCount,
                    summary.updatedCount,
                    summary.failureCount);
            
        } catch (Exception e) {
            log.error("Scheduled symbol sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * 同步所有启用数据源的交易对
     * 
     * @return 同步结果摘要
     */
    public SymbolSyncSummary syncAllSymbols() {
        log.info("Starting symbol sync for all enabled data sources");
        
        SymbolSyncSummary summary = new SymbolSyncSummary();
        
        // 获取所有启用的市场（已包含数据源状态检查）
        List<MarketDTO> enabledMarkets = marketService.listAll(null, null, true);
        
        for (MarketDTO market : enabledMarkets) {
            try {
                // 检查数据源是否启用
                if (!isDataSourceEnabled(market.getDataSourceId())) {
                    log.debug("Skipping market {} - data source disabled", market.getName());
                    continue;
                }
                
                log.info("Syncing symbols for market: {} ({})", market.getName(), market.getMarketType());
                
                SymbolSyncResult result = symbolService.syncSymbolsFromBinance(market.getId());
                
                if (result.isSuccess()) {
                    summary.marketsProcessed++;
                    summary.totalSymbols += result.getSyncedCount();
                    summary.createdCount += result.getCreatedCount();
                    summary.updatedCount += result.getUpdatedCount();
                    
                    log.info("Synced symbols for market {}: {} total, {} created, {} updated",
                            market.getName(), result.getSyncedCount(), 
                            result.getCreatedCount(), result.getUpdatedCount());
                } else {
                    summary.failureCount++;
                    log.error("Failed to sync symbols for market {}: {}", 
                            market.getName(), result.getMessage());
                }
                
            } catch (Exception e) {
                summary.failureCount++;
                log.error("Error syncing symbols for market {}: {}", 
                        market.getName(), e.getMessage());
            }
        }
        
        return summary;
    }

    /**
     * 检查数据源是否启用
     */
    private boolean isDataSourceEnabled(Long dataSourceId) {
        try {
            var dataSource = dataSourceService.findById(dataSourceId);
            return dataSource.getEnabled() && !dataSource.getDeleted();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 同步结果摘要
     */
    public static class SymbolSyncSummary {
        public int marketsProcessed = 0;
        public int totalSymbols = 0;
        public int createdCount = 0;
        public int updatedCount = 0;
        public int failureCount = 0;
    }
}

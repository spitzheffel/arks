package com.chanlun.scheduler;

import com.chanlun.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 配置刷新定时任务
 * 
 * 定期刷新系统配置缓存，确保定时任务使用最新的配置
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigRefreshScheduler {

    private final SystemConfigService systemConfigService;
    private final DynamicSchedulerManager dynamicSchedulerManager;

    /**
     * 定期刷新配置缓存
     * 
     * 每 5 分钟执行一次，确保配置变更能够及时生效
     */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000) // 5 分钟
    public void refreshConfigCache() {
        log.debug("Refreshing system config cache");
        
        try {
            // 刷新配置缓存
            systemConfigService.refreshCache();
            
            // 刷新动态任务配置（如果启用了动态调度）
            // dynamicSchedulerManager.refreshAllConfigs();
            
            log.debug("System config cache refreshed");
            
        } catch (Exception e) {
            log.error("Failed to refresh config cache: {}", e.getMessage());
        }
    }

    /**
     * 手动触发配置刷新
     */
    public void triggerRefresh() {
        log.info("Manual config refresh triggered");
        systemConfigService.refreshCache();
        dynamicSchedulerManager.refreshAllConfigs();
    }
}

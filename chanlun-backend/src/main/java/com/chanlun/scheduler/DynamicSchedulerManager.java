package com.chanlun.scheduler;

import com.chanlun.entity.SystemConfig;
import com.chanlun.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态定时任务管理器
 * 
 * 支持从数据库读取 Cron 表达式并动态调度任务
 * 支持运行时刷新配置
 * 
 * @author Chanlun Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicSchedulerManager {

    private final TaskScheduler taskScheduler;
    private final SystemConfigService systemConfigService;
    private final SymbolSyncScheduler symbolSyncScheduler;
    private final HistorySyncScheduler historySyncScheduler;
    private final GapDetectScheduler gapDetectScheduler;

    /**
     * UTC 时区
     */
    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    /**
     * 已调度的任务
     */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 当前使用的 Cron 表达式
     */
    private final Map<String, String> currentCronExpressions = new ConcurrentHashMap<>();

    /**
     * 任务名称常量
     */
    private static final String TASK_SYMBOL_SYNC = "symbolSync";
    private static final String TASK_HISTORY_SYNC = "historySync";
    private static final String TASK_GAP_DETECT = "gapDetect";

    /**
     * 初始化动态任务
     */
    @PostConstruct
    public void init() {
        log.info("Initializing dynamic scheduler manager");
        
        // 注册动态任务（使用数据库配置的 Cron 表达式）
        // 注意：这些任务会覆盖 @Scheduled 注解定义的任务
        // 如果需要完全动态化，可以移除 @Scheduled 注解
        
        // 暂时不启用动态调度，保持使用 @Scheduled 注解
        // 如果需要动态调度，取消下面的注释
        // scheduleSymbolSync();
        // scheduleHistorySync();
        
        log.info("Dynamic scheduler manager initialized");
    }

    /**
     * 销毁时取消所有任务
     */
    @PreDestroy
    public void destroy() {
        log.info("Destroying dynamic scheduler manager");
        cancelAllTasks();
    }

    /**
     * 调度交易对同步任务
     */
    public void scheduleSymbolSync() {
        String cronExpression = systemConfigService.getSymbolSyncCron();
        scheduleTask(TASK_SYMBOL_SYNC, cronExpression, () -> {
            try {
                symbolSyncScheduler.syncAllSymbols();
            } catch (Exception e) {
                log.error("Dynamic symbol sync task failed: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 调度历史数据同步任务
     */
    public void scheduleHistorySync() {
        String cronExpression = systemConfigService.getHistorySyncCron();
        scheduleTask(TASK_HISTORY_SYNC, cronExpression, () -> {
            try {
                // 检查自动同步开关
                if (systemConfigService.isHistoryAutoSyncEnabled()) {
                    historySyncScheduler.triggerManualSync();
                } else {
                    log.debug("History auto sync is disabled, skipping dynamic task");
                }
            } catch (Exception e) {
                log.error("Dynamic history sync task failed: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 调度缺口检测任务
     */
    public void scheduleGapDetect() {
        String cronExpression = systemConfigService.getGapDetectCron();
        scheduleTask(TASK_GAP_DETECT, cronExpression, () -> {
            try {
                gapDetectScheduler.triggerManualDetect();
            } catch (Exception e) {
                log.error("Dynamic gap detect task failed: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 调度任务
     * 
     * @param taskName 任务名称
     * @param cronExpression Cron 表达式
     * @param task 任务
     */
    private void scheduleTask(String taskName, String cronExpression, Runnable task) {
        // 取消已存在的任务
        cancelTask(taskName);
        
        try {
            CronTrigger trigger = new CronTrigger(cronExpression, UTC_TIMEZONE);
            ScheduledFuture<?> future = taskScheduler.schedule(task, trigger);
            
            scheduledTasks.put(taskName, future);
            currentCronExpressions.put(taskName, cronExpression);
            
            log.info("Scheduled task '{}' with cron: {}", taskName, cronExpression);
            
        } catch (Exception e) {
            log.error("Failed to schedule task '{}' with cron '{}': {}", 
                    taskName, cronExpression, e.getMessage());
        }
    }

    /**
     * 取消任务
     * 
     * @param taskName 任务名称
     */
    public void cancelTask(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskName);
        if (future != null) {
            future.cancel(false);
            currentCronExpressions.remove(taskName);
            log.info("Cancelled task: {}", taskName);
        }
    }

    /**
     * 取消所有任务
     */
    public void cancelAllTasks() {
        scheduledTasks.forEach((name, future) -> {
            future.cancel(false);
            log.info("Cancelled task: {}", name);
        });
        scheduledTasks.clear();
        currentCronExpressions.clear();
    }

    /**
     * 刷新所有动态任务的配置
     * 
     * 从数据库重新读取 Cron 表达式，如果有变化则重新调度任务
     */
    public void refreshAllConfigs() {
        log.info("Refreshing all dynamic task configurations");
        
        // 刷新配置缓存
        systemConfigService.refreshCache();
        
        // 检查并更新交易对同步任务
        refreshTaskConfig(TASK_SYMBOL_SYNC, 
                SystemConfig.Keys.SYNC_SYMBOL_CRON,
                this::scheduleSymbolSync);
        
        // 检查并更新历史同步任务
        refreshTaskConfig(TASK_HISTORY_SYNC, 
                SystemConfig.Keys.SYNC_HISTORY_CRON,
                this::scheduleHistorySync);
        
        // 检查并更新缺口检测任务
        refreshTaskConfig(TASK_GAP_DETECT, 
                SystemConfig.Keys.SYNC_GAP_DETECT_CRON,
                this::scheduleGapDetect);
        
        log.info("Dynamic task configurations refreshed");
    }

    /**
     * 刷新单个任务的配置
     * 
     * @param taskName 任务名称
     * @param configKey 配置键
     * @param scheduleAction 调度动作
     */
    private void refreshTaskConfig(String taskName, String configKey, Runnable scheduleAction) {
        String newCron = systemConfigService.getValueForceRefresh(configKey);
        String currentCron = currentCronExpressions.get(taskName);
        
        if (newCron != null && !newCron.equals(currentCron)) {
            log.info("Cron expression changed for task '{}': {} -> {}", 
                    taskName, currentCron, newCron);
            scheduleAction.run();
        }
    }

    /**
     * 获取任务的当前 Cron 表达式
     * 
     * @param taskName 任务名称
     * @return Cron 表达式
     */
    public String getCurrentCronExpression(String taskName) {
        return currentCronExpressions.get(taskName);
    }

    /**
     * 检查任务是否正在运行
     * 
     * @param taskName 任务名称
     * @return 是否正在运行
     */
    public boolean isTaskScheduled(String taskName) {
        ScheduledFuture<?> future = scheduledTasks.get(taskName);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    /**
     * 获取所有已调度的任务名称
     * 
     * @return 任务名称列表
     */
    public java.util.Set<String> getScheduledTaskNames() {
        return scheduledTasks.keySet();
    }
}

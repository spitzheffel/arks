package com.chanlun.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.TimeZone;

/**
 * 定时任务配置
 * 
 * 配置定时任务执行器，统一使用 UTC 时区
 * 
 * @author Chanlun Team
 */
@Slf4j
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    /**
     * 定时任务线程池大小
     */
    private static final int POOL_SIZE = 5;

    /**
     * 线程名称前缀
     */
    private static final String THREAD_NAME_PREFIX = "scheduler-";

    /**
     * UTC 时区
     */
    public static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // 设置默认时区为 UTC
        TimeZone.setDefault(UTC_TIMEZONE);
        log.info("Default timezone set to UTC for scheduled tasks");
        
        // 配置任务调度器
        taskRegistrar.setTaskScheduler(taskScheduler());
    }

    /**
     * 创建任务调度器
     * 
     * @return TaskScheduler
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix(THREAD_NAME_PREFIX);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> 
            log.error("Scheduled task error: {}", throwable.getMessage(), throwable));
        scheduler.setRejectedExecutionHandler((r, executor) -> 
            log.warn("Scheduled task rejected: {}", r.toString()));
        scheduler.initialize();
        
        log.info("TaskScheduler initialized with pool size: {}", POOL_SIZE);
        return scheduler;
    }
}

package com.chanlun.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 定时任务配置测试
 */
class SchedulerConfigTest {

    @Test
    @DisplayName("UTC 时区常量正确")
    void utcTimezoneConstant() {
        assertEquals("UTC", SchedulerConfig.UTC_TIMEZONE.getID());
    }

    @Test
    @DisplayName("创建 TaskScheduler")
    void taskScheduler() {
        SchedulerConfig config = new SchedulerConfig();
        TaskScheduler scheduler = config.taskScheduler();

        assertNotNull(scheduler);
        assertTrue(scheduler instanceof ThreadPoolTaskScheduler);
    }

    @Test
    @DisplayName("配置任务时设置默认时区为 UTC")
    void configureTasks_SetsDefaultTimezoneToUtc() {
        SchedulerConfig config = new SchedulerConfig();
        
        // 先设置一个非 UTC 时区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        
        // 调用配置方法
        config.configureTasks(new org.springframework.scheduling.config.ScheduledTaskRegistrar());
        
        // 验证默认时区已设置为 UTC
        assertEquals("UTC", TimeZone.getDefault().getID());
    }
}

package com.chanlun.scheduler;

import com.chanlun.service.SystemConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * 配置刷新定时任务测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigRefreshSchedulerTest {

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private DynamicSchedulerManager dynamicSchedulerManager;

    @InjectMocks
    private ConfigRefreshScheduler configRefreshScheduler;

    @Test
    @DisplayName("定期刷新配置缓存")
    void refreshConfigCache() {
        configRefreshScheduler.refreshConfigCache();

        verify(systemConfigService).refreshCache();
    }

    @Test
    @DisplayName("定期刷新配置缓存 - 异常处理")
    void refreshConfigCache_ExceptionHandling() {
        doThrow(new RuntimeException("刷新失败")).when(systemConfigService).refreshCache();

        assertDoesNotThrow(() -> configRefreshScheduler.refreshConfigCache());
    }

    @Test
    @DisplayName("手动触发配置刷新")
    void triggerRefresh() {
        configRefreshScheduler.triggerRefresh();

        verify(systemConfigService).refreshCache();
        verify(dynamicSchedulerManager).refreshAllConfigs();
    }
}

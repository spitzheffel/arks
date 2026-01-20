package com.chanlun.service;

import com.chanlun.entity.SystemConfig;
import com.chanlun.event.RealtimeSyncConfigChangedEvent;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.SystemConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SystemConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SystemConfigService systemConfigService;

    private SystemConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = SystemConfig.builder()
                .id(1L)
                .configKey("sync.history.auto")
                .configValue("true")
                .description("历史数据自动同步开关")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        
        // 清除缓存
        systemConfigService.refreshCache();
    }

    @Test
    @DisplayName("获取所有配置")
    void getAll() {
        List<SystemConfig> configs = Arrays.asList(testConfig);
        when(systemConfigMapper.selectList(any())).thenReturn(configs);

        List<SystemConfig> result = systemConfigService.getAll();

        assertEquals(1, result.size());
        assertEquals("sync.history.auto", result.get(0).getConfigKey());
    }

    @Test
    @DisplayName("根据键获取配置 - 存在")
    void getByKey_Exists() {
        when(systemConfigMapper.selectByKey("sync.history.auto")).thenReturn(testConfig);

        SystemConfig result = systemConfigService.getByKey("sync.history.auto");

        assertNotNull(result);
        assertEquals("true", result.getConfigValue());
    }

    @Test
    @DisplayName("根据键获取配置 - 不存在抛出异常")
    void getByKey_NotExists_ThrowsException() {
        when(systemConfigMapper.selectByKey("not.exists")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, 
                () -> systemConfigService.getByKey("not.exists"));
    }

    @Test
    @DisplayName("获取配置值 - 使用缓存")
    void getValue_UsesCache() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto")).thenReturn("true");

        // 第一次调用
        String value1 = systemConfigService.getValue("sync.history.auto");
        // 第二次调用应该使用缓存
        String value2 = systemConfigService.getValue("sync.history.auto");

        assertEquals("true", value1);
        assertEquals("true", value2);
        // 只应该调用一次数据库
        verify(systemConfigMapper, times(1)).selectValueByKey("sync.history.auto");
    }

    @Test
    @DisplayName("获取配置值 - 带默认值")
    void getValue_WithDefault() {
        when(systemConfigMapper.selectValueByKey("not.exists")).thenReturn(null);

        String result = systemConfigService.getValue("not.exists", "default");

        assertEquals("default", result);
    }

    @Test
    @DisplayName("获取布尔配置值 - true")
    void getBooleanValue_True() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto")).thenReturn("true");

        boolean result = systemConfigService.getBooleanValue("sync.history.auto", false);

        assertTrue(result);
    }

    @Test
    @DisplayName("获取布尔配置值 - false")
    void getBooleanValue_False() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto")).thenReturn("false");

        boolean result = systemConfigService.getBooleanValue("sync.history.auto", true);

        assertFalse(result);
    }

    @Test
    @DisplayName("获取布尔配置值 - 使用默认值")
    void getBooleanValue_Default() {
        when(systemConfigMapper.selectValueByKey("not.exists")).thenReturn(null);

        boolean result = systemConfigService.getBooleanValue("not.exists", true);

        assertTrue(result);
    }

    @Test
    @DisplayName("获取整数配置值")
    void getIntValue() {
        when(systemConfigMapper.selectValueByKey("sync.gap_fill.max_retry")).thenReturn("5");

        int result = systemConfigService.getIntValue("sync.gap_fill.max_retry", 3);

        assertEquals(5, result);
    }

    @Test
    @DisplayName("获取整数配置值 - 无效值使用默认值")
    void getIntValue_InvalidValue() {
        when(systemConfigMapper.selectValueByKey("sync.gap_fill.max_retry")).thenReturn("invalid");

        int result = systemConfigService.getIntValue("sync.gap_fill.max_retry", 3);

        assertEquals(3, result);
    }

    @Test
    @DisplayName("获取长整数配置值")
    void getLongValue() {
        when(systemConfigMapper.selectValueByKey("sync.gap_fill.interval_ms")).thenReturn("2000");

        long result = systemConfigService.getLongValue("sync.gap_fill.interval_ms", 1000);

        assertEquals(2000L, result);
    }

    @Test
    @DisplayName("更新配置值")
    void updateValue() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto")).thenReturn("true");
        when(systemConfigMapper.updateValueByKey("sync.history.auto", "false")).thenReturn(1);

        boolean result = systemConfigService.updateValue("sync.history.auto", "false");

        assertTrue(result);
        verify(systemConfigMapper).updateValueByKey("sync.history.auto", "false");
    }

    @Test
    @DisplayName("更新实时同步开关 - 发布事件")
    void updateValue_RealtimeSyncEnabled_PublishesEvent() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED))
                .thenReturn("true");
        when(systemConfigMapper.updateValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED, "false"))
                .thenReturn(1);

        systemConfigService.updateValue(SystemConfig.Keys.SYNC_REALTIME_ENABLED, "false");

        ArgumentCaptor<RealtimeSyncConfigChangedEvent> eventCaptor = 
                ArgumentCaptor.forClass(RealtimeSyncConfigChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertFalse(eventCaptor.getValue().isEnabled());
    }

    @Test
    @DisplayName("刷新缓存")
    void refreshCache() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto")).thenReturn("true");
        
        // 第一次调用
        systemConfigService.getValue("sync.history.auto");
        // 刷新缓存
        systemConfigService.refreshCache();
        // 第二次调用应该重新查询数据库
        systemConfigService.getValue("sync.history.auto");

        verify(systemConfigMapper, times(2)).selectValueByKey("sync.history.auto");
    }

    @Test
    @DisplayName("强制刷新获取配置值")
    void getValueForceRefresh() {
        when(systemConfigMapper.selectValueByKey("sync.history.auto"))
                .thenReturn("true")
                .thenReturn("false");

        // 第一次调用
        String value1 = systemConfigService.getValue("sync.history.auto");
        // 强制刷新
        String value2 = systemConfigService.getValueForceRefresh("sync.history.auto");

        assertEquals("true", value1);
        assertEquals("false", value2);
        verify(systemConfigMapper, times(2)).selectValueByKey("sync.history.auto");
    }

    @Test
    @DisplayName("检查历史自动同步是否启用")
    void isHistoryAutoSyncEnabled() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_AUTO))
                .thenReturn("true");

        assertTrue(systemConfigService.isHistoryAutoSyncEnabled());
    }

    @Test
    @DisplayName("检查实时同步是否启用")
    void isRealtimeSyncEnabled() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED))
                .thenReturn("true");

        assertTrue(systemConfigService.isRealtimeSyncEnabled());
    }

    @Test
    @DisplayName("检查自动缺口回补是否启用")
    void isAutoGapFillEnabled() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_AUTO))
                .thenReturn("false");

        assertFalse(systemConfigService.isAutoGapFillEnabled());
    }

    @Test
    @DisplayName("获取历史同步 Cron 表达式")
    void getHistorySyncCron() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_CRON))
                .thenReturn("0 30 3 * * ?");

        String result = systemConfigService.getHistorySyncCron();

        assertEquals("0 30 3 * * ?", result);
    }

    @Test
    @DisplayName("获取交易对同步 Cron 表达式")
    void getSymbolSyncCron() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_SYMBOL_CRON))
                .thenReturn("0 0 2 * * ?");

        String result = systemConfigService.getSymbolSyncCron();

        assertEquals("0 0 2 * * ?", result);
    }

    @Test
    @DisplayName("获取缺口回补配置")
    void getGapFillConfigs() {
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_MAX_RETRY))
                .thenReturn("5");
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_BATCH_SIZE))
                .thenReturn("20");
        when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_INTERVAL_MS))
                .thenReturn("2000");

        assertEquals(5, systemConfigService.getGapFillMaxRetry());
        assertEquals(20, systemConfigService.getGapFillBatchSize());
        assertEquals(2000L, systemConfigService.getGapFillIntervalMs());
    }
}

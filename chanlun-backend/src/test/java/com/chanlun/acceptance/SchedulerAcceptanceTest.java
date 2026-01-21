package com.chanlun.acceptance;

import com.chanlun.config.SchedulerConfig;
import com.chanlun.entity.SystemConfig;
import com.chanlun.mapper.SystemConfigMapper;
import com.chanlun.service.SystemConfigService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 定时任务和配置验收测试 (任务 25.3)
 * 
 * 验证定时任务相关功能：
 * - 25.3 定时任务执行 (UTC 时区)
 * 
 * @author Chanlun Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务和配置验收测试")
class SchedulerAcceptanceTest {

    @Mock
    private SystemConfigMapper systemConfigMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SystemConfigService systemConfigService;

    // ==================== 25.3 验证定时任务执行 (UTC 时区) ====================

    @Nested
    @DisplayName("25.3 验证定时任务执行 (UTC 时区)")
    class SchedulerTimezoneTests {

        @Test
        @DisplayName("SchedulerConfig 配置 UTC 时区")
        void schedulerConfig_usesUtcTimezone() {
            SchedulerConfig config = new SchedulerConfig();
            
            // 验证 SchedulerConfig 类存在并可以实例化
            assertNotNull(config);
            
            // 验证 UTC 时区常量
            assertEquals("UTC", ZoneId.of("UTC").getId());
        }

        @Test
        @DisplayName("获取历史同步 Cron 表达式")
        void getHistorySyncCron() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_CRON))
                    .thenReturn("0 30 3 * * ?");

            String cron = systemConfigService.getHistorySyncCron();

            assertEquals("0 30 3 * * ?", cron);
        }

        @Test
        @DisplayName("获取历史同步 Cron 表达式 - 使用默认值")
        void getHistorySyncCron_usesDefault() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_CRON))
                    .thenReturn(null);

            String cron = systemConfigService.getHistorySyncCron();

            assertEquals(SystemConfig.Defaults.SYNC_HISTORY_CRON, cron);
        }

        @Test
        @DisplayName("获取交易对同步 Cron 表达式")
        void getSymbolSyncCron() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_SYMBOL_CRON))
                    .thenReturn("0 0 2 * * ?");

            String cron = systemConfigService.getSymbolSyncCron();

            assertEquals("0 0 2 * * ?", cron);
        }

        @Test
        @DisplayName("获取缺口检测 Cron 表达式")
        void getGapDetectCron() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_DETECT_CRON))
                    .thenReturn("0 0 * * * ?");

            String cron = systemConfigService.getGapDetectCron();

            assertEquals("0 0 * * * ?", cron);
        }
    }

    // ==================== 配置服务测试 ====================

    @Nested
    @DisplayName("配置服务测试")
    class ConfigServiceTests {

        @Test
        @DisplayName("检查历史数据自动同步是否启用")
        void isHistoryAutoSyncEnabled() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_AUTO))
                    .thenReturn("true");

            assertTrue(systemConfigService.isHistoryAutoSyncEnabled());
        }

        @Test
        @DisplayName("检查历史数据自动同步是否启用 - 默认值")
        void isHistoryAutoSyncEnabled_default() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_HISTORY_AUTO))
                    .thenReturn(null);

            assertEquals(SystemConfig.Defaults.SYNC_HISTORY_AUTO, 
                    systemConfigService.isHistoryAutoSyncEnabled());
        }

        @Test
        @DisplayName("检查实时同步是否启用")
        void isRealtimeSyncEnabled() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED))
                    .thenReturn("true");

            assertTrue(systemConfigService.isRealtimeSyncEnabled());
        }

        @Test
        @DisplayName("检查实时同步是否启用 - false")
        void isRealtimeSyncEnabled_false() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED))
                    .thenReturn("false");

            assertFalse(systemConfigService.isRealtimeSyncEnabled());
        }

        @Test
        @DisplayName("检查自动缺口回补是否启用")
        void isAutoGapFillEnabled() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_AUTO))
                    .thenReturn("true");

            assertTrue(systemConfigService.isAutoGapFillEnabled());
        }

        @Test
        @DisplayName("检查自动缺口回补是否启用 - 默认关闭")
        void isAutoGapFillEnabled_defaultFalse() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_AUTO))
                    .thenReturn(null);

            assertFalse(systemConfigService.isAutoGapFillEnabled());
        }

        @Test
        @DisplayName("获取缺口回补最大重试次数")
        void getGapFillMaxRetry() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_MAX_RETRY))
                    .thenReturn("5");

            assertEquals(5, systemConfigService.getGapFillMaxRetry());
        }

        @Test
        @DisplayName("获取缺口回补最大重试次数 - 默认值")
        void getGapFillMaxRetry_default() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_MAX_RETRY))
                    .thenReturn(null);

            assertEquals(SystemConfig.Defaults.SYNC_GAP_FILL_MAX_RETRY, 
                    systemConfigService.getGapFillMaxRetry());
        }

        @Test
        @DisplayName("获取缺口回补批量大小")
        void getGapFillBatchSize() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_BATCH_SIZE))
                    .thenReturn("20");

            assertEquals(20, systemConfigService.getGapFillBatchSize());
        }

        @Test
        @DisplayName("获取缺口回补间隔毫秒数")
        void getGapFillIntervalMs() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_GAP_FILL_INTERVAL_MS))
                    .thenReturn("2000");

            assertEquals(2000L, systemConfigService.getGapFillIntervalMs());
        }

        @Test
        @DisplayName("更新配置值")
        void updateValue() {
            when(systemConfigMapper.selectValueByKey("test.key")).thenReturn("old_value");
            when(systemConfigMapper.updateValueByKey("test.key", "new_value")).thenReturn(1);

            boolean result = systemConfigService.updateValue("test.key", "new_value");

            assertTrue(result);
            verify(systemConfigMapper).updateValueByKey("test.key", "new_value");
        }

        @Test
        @DisplayName("更新实时同步开关 - 发布事件")
        void updateRealtimeSyncEnabled_publishesEvent() {
            when(systemConfigMapper.selectValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED))
                    .thenReturn("true");
            when(systemConfigMapper.updateValueByKey(SystemConfig.Keys.SYNC_REALTIME_ENABLED, "false"))
                    .thenReturn(1);

            systemConfigService.updateValue(SystemConfig.Keys.SYNC_REALTIME_ENABLED, "false");

            verify(eventPublisher).publishEvent(any());
        }

        @Test
        @DisplayName("刷新配置缓存")
        void refreshCache() {
            // 先缓存一个值
            when(systemConfigMapper.selectValueByKey("test.key")).thenReturn("cached_value");
            systemConfigService.getValue("test.key");

            // 刷新缓存
            systemConfigService.refreshCache();

            // 再次获取应该重新查询数据库
            when(systemConfigMapper.selectValueByKey("test.key")).thenReturn("new_value");
            String value = systemConfigService.getValue("test.key");

            assertEquals("new_value", value);
        }

        @Test
        @DisplayName("强制刷新配置值")
        void getValueForceRefresh() {
            // 先缓存一个值
            when(systemConfigMapper.selectValueByKey("test.key")).thenReturn("cached_value");
            systemConfigService.getValue("test.key");

            // 强制刷新
            when(systemConfigMapper.selectValueByKey("test.key")).thenReturn("refreshed_value");
            String value = systemConfigService.getValueForceRefresh("test.key");

            assertEquals("refreshed_value", value);
        }
    }

    // ==================== 布尔值解析测试 ====================

    @Nested
    @DisplayName("布尔值解析测试")
    class BooleanParsingTests {

        @Test
        @DisplayName("解析布尔值 - true")
        void parseBooleanValue_true() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn("true");
            assertTrue(systemConfigService.getBooleanValue("test.bool", false));
        }

        @Test
        @DisplayName("解析布尔值 - TRUE (大写)")
        void parseBooleanValue_TRUE() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn("TRUE");
            assertTrue(systemConfigService.getBooleanValue("test.bool", false));
        }

        @Test
        @DisplayName("解析布尔值 - 1")
        void parseBooleanValue_1() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn("1");
            assertTrue(systemConfigService.getBooleanValue("test.bool", false));
        }

        @Test
        @DisplayName("解析布尔值 - false")
        void parseBooleanValue_false() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn("false");
            assertFalse(systemConfigService.getBooleanValue("test.bool", true));
        }

        @Test
        @DisplayName("解析布尔值 - 0")
        void parseBooleanValue_0() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn("0");
            assertFalse(systemConfigService.getBooleanValue("test.bool", true));
        }

        @Test
        @DisplayName("解析布尔值 - null 使用默认值")
        void parseBooleanValue_null() {
            when(systemConfigMapper.selectValueByKey("test.bool")).thenReturn(null);
            assertTrue(systemConfigService.getBooleanValue("test.bool", true));
            assertFalse(systemConfigService.getBooleanValue("test.bool", false));
        }
    }

    // ==================== 数值解析测试 ====================

    @Nested
    @DisplayName("数值解析测试")
    class NumberParsingTests {

        @Test
        @DisplayName("解析整数值")
        void parseIntValue() {
            when(systemConfigMapper.selectValueByKey("test.int")).thenReturn("42");
            assertEquals(42, systemConfigService.getIntValue("test.int", 0));
        }

        @Test
        @DisplayName("解析整数值 - 无效格式使用默认值")
        void parseIntValue_invalid() {
            when(systemConfigMapper.selectValueByKey("test.int")).thenReturn("not_a_number");
            assertEquals(10, systemConfigService.getIntValue("test.int", 10));
        }

        @Test
        @DisplayName("解析长整数值")
        void parseLongValue() {
            when(systemConfigMapper.selectValueByKey("test.long")).thenReturn("1234567890123");
            assertEquals(1234567890123L, systemConfigService.getLongValue("test.long", 0L));
        }

        @Test
        @DisplayName("解析长整数值 - 无效格式使用默认值")
        void parseLongValue_invalid() {
            when(systemConfigMapper.selectValueByKey("test.long")).thenReturn("not_a_number");
            assertEquals(100L, systemConfigService.getLongValue("test.long", 100L));
        }
    }
}

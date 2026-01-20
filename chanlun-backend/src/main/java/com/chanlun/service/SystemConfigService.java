package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chanlun.entity.SystemConfig;
import com.chanlun.event.RealtimeSyncConfigChangedEvent;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务
 * 
 * 提供系统配置的读取和更新功能
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 配置缓存，用于减少数据库查询
     */
    private final Map<String, CachedConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 缓存过期时间（毫秒）
     */
    private static final long CACHE_TTL_MS = 60_000; // 1 分钟

    /**
     * 缓存配置记录
     */
    private record CachedConfig(String value, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    /**
     * 获取所有配置
     * 
     * @return 配置列表
     */
    public List<SystemConfig> getAll() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .orderByAsc(SystemConfig::getConfigKey));
    }

    /**
     * 根据配置键获取配置
     * 
     * @param key 配置键
     * @return 系统配置
     */
    public SystemConfig getByKey(String key) {
        SystemConfig config = systemConfigMapper.selectByKey(key);
        if (config == null) {
            throw new ResourceNotFoundException("配置不存在: " + key);
        }
        return config;
    }

    /**
     * 根据配置键获取配置值
     * 
     * @param key 配置键
     * @return 配置值
     */
    public String getValue(String key) {
        // 先检查缓存
        CachedConfig cached = configCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return cached.value();
        }
        
        // 从数据库查询
        String value = systemConfigMapper.selectValueByKey(key);
        
        // 更新缓存
        if (value != null) {
            configCache.put(key, new CachedConfig(value, System.currentTimeMillis()));
        }
        
        return value;
    }

    /**
     * 根据配置键获取配置值，如果不存在则返回默认值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public String getValue(String key, String defaultValue) {
        String value = systemConfigMapper.selectValueByKey(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取布尔类型配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * 获取整数类型配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 整数值
     */
    public int getIntValue(String key, int defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer config value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * 获取长整数类型配置值
     * 
     * @param key 配置键
     * @param defaultValue 默认值
     * @return 长整数值
     */
    public long getLongValue(String key, long defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long config value for key {}: {}", key, value);
            return defaultValue;
        }
    }

    /**
     * 更新配置值
     * 
     * @param key 配置键
     * @param value 配置值
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateValue(String key, String value) {
        // 获取旧值用于比较
        String oldValue = getValue(key);
        
        int updated = systemConfigMapper.updateValueByKey(key, value);
        if (updated > 0) {
            log.info("Updated config: key={}, value={}", key, value);
            
            // 清除缓存
            configCache.remove(key);
            
            // 如果是实时同步开关变更，发布事件
            if (SystemConfig.Keys.SYNC_REALTIME_ENABLED.equals(key)) {
                boolean newEnabled = "true".equalsIgnoreCase(value) || "1".equals(value);
                boolean oldEnabled = "true".equalsIgnoreCase(oldValue) || "1".equals(oldValue);
                
                if (newEnabled != oldEnabled) {
                    log.info("Publishing RealtimeSyncConfigChangedEvent: enabled={}", newEnabled);
                    eventPublisher.publishEvent(new RealtimeSyncConfigChangedEvent(this, newEnabled));
                }
            }
        }
        return updated > 0;
    }

    /**
     * 刷新配置缓存
     * 
     * 清除所有缓存，下次读取时会从数据库重新加载
     */
    public void refreshCache() {
        configCache.clear();
        log.info("Config cache cleared");
    }

    /**
     * 刷新指定配置的缓存
     * 
     * @param key 配置键
     */
    public void refreshCache(String key) {
        configCache.remove(key);
        log.debug("Config cache cleared for key: {}", key);
    }

    /**
     * 强制从数据库重新加载配置值（绕过缓存）
     * 
     * @param key 配置键
     * @return 配置值
     */
    public String getValueForceRefresh(String key) {
        String value = systemConfigMapper.selectValueByKey(key);
        if (value != null) {
            configCache.put(key, new CachedConfig(value, System.currentTimeMillis()));
        } else {
            configCache.remove(key);
        }
        return value;
    }

    // ==================== 便捷方法 ====================

    /**
     * 检查历史数据自动同步是否启用
     */
    public boolean isHistoryAutoSyncEnabled() {
        return getBooleanValue(SystemConfig.Keys.SYNC_HISTORY_AUTO, 
                SystemConfig.Defaults.SYNC_HISTORY_AUTO);
    }

    /**
     * 检查实时同步是否启用
     */
    public boolean isRealtimeSyncEnabled() {
        return getBooleanValue(SystemConfig.Keys.SYNC_REALTIME_ENABLED, 
                SystemConfig.Defaults.SYNC_REALTIME_ENABLED);
    }

    /**
     * 检查自动缺口回补是否启用
     */
    public boolean isAutoGapFillEnabled() {
        return getBooleanValue(SystemConfig.Keys.SYNC_GAP_FILL_AUTO, 
                SystemConfig.Defaults.SYNC_GAP_FILL_AUTO);
    }

    /**
     * 获取历史同步 Cron 表达式
     */
    public String getHistorySyncCron() {
        return getValue(SystemConfig.Keys.SYNC_HISTORY_CRON, 
                SystemConfig.Defaults.SYNC_HISTORY_CRON);
    }

    /**
     * 获取交易对同步 Cron 表达式
     */
    public String getSymbolSyncCron() {
        return getValue(SystemConfig.Keys.SYNC_SYMBOL_CRON, 
                SystemConfig.Defaults.SYNC_SYMBOL_CRON);
    }

    /**
     * 获取缺口检测 Cron 表达式
     */
    public String getGapDetectCron() {
        return getValue(SystemConfig.Keys.SYNC_GAP_DETECT_CRON, 
                SystemConfig.Defaults.SYNC_GAP_DETECT_CRON);
    }

    /**
     * 获取缺口回补最大重试次数
     */
    public int getGapFillMaxRetry() {
        return getIntValue(SystemConfig.Keys.SYNC_GAP_FILL_MAX_RETRY, 
                SystemConfig.Defaults.SYNC_GAP_FILL_MAX_RETRY);
    }

    /**
     * 获取缺口回补批量大小
     */
    public int getGapFillBatchSize() {
        return getIntValue(SystemConfig.Keys.SYNC_GAP_FILL_BATCH_SIZE, 
                SystemConfig.Defaults.SYNC_GAP_FILL_BATCH_SIZE);
    }

    /**
     * 获取缺口回补间隔毫秒数
     */
    public long getGapFillIntervalMs() {
        return getLongValue(SystemConfig.Keys.SYNC_GAP_FILL_INTERVAL_MS, 
                SystemConfig.Defaults.SYNC_GAP_FILL_INTERVAL_MS);
    }
}

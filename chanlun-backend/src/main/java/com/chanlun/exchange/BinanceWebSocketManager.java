package com.chanlun.exchange;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Kline;
import com.chanlun.entity.Symbol;
import com.chanlun.enums.MarketType;
import com.chanlun.exchange.model.BinanceWsKlineEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;

/**
 * 币安 WebSocket 管理器
 * 
 * 管理多个 WebSocket 连接，支持：
 * - 动态订阅/取消订阅
 * - 按数据源分组管理
 * - 统一的 K 线数据回调
 * - 连接状态监控
 * 
 * @author Chanlun Team
 */
@Slf4j
public class BinanceWebSocketManager {

    /**
     * 订阅信息
     */
    @Getter
    public static class SubscriptionInfo {
        private final Long dataSourceId;
        private final Long symbolId;
        private final String symbolCode;
        private final String interval;
        private final BinanceWebSocketClient client;
        private final Instant subscribedAt;

        public SubscriptionInfo(Long dataSourceId, Long symbolId, String symbolCode, 
                                String interval, BinanceWebSocketClient client) {
            this.dataSourceId = dataSourceId;
            this.symbolId = symbolId;
            this.symbolCode = symbolCode;
            this.interval = interval;
            this.client = client;
            this.subscribedAt = Instant.now();
        }

        public String getSubscriptionKey() {
            return dataSourceId + "_" + symbolId + "_" + interval;
        }

        public boolean isConnected() {
            return client != null && client.isConnected();
        }
    }

    /**
     * 所有订阅 (key: dataSourceId_symbolId_interval)
     */
    private final Map<String, SubscriptionInfo> subscriptions = new ConcurrentHashMap<>();

    /**
     * 按数据源分组的订阅 (key: dataSourceId)
     */
    private final Map<Long, Set<String>> subscriptionsByDataSource = new ConcurrentHashMap<>();

    /**
     * 按交易对分组的订阅 (key: symbolId)
     */
    private final Map<Long, Set<String>> subscriptionsBySymbol = new ConcurrentHashMap<>();

    /**
     * K 线数据回调 (symbolId, klineEvent)
     */
    private BiConsumer<Long, BinanceWsKlineEvent> klineCallback;

    /**
     * 断线回调 (subscriptionKey)
     */
    private java.util.function.Consumer<String> disconnectCallback;

    /**
     * 是否启用 Mock 模式
     */
    private final boolean mockEnabled;

    /**
     * 读写锁
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构造函数
     * 
     * @param mockEnabled 是否启用 Mock 模式
     */
    public BinanceWebSocketManager(boolean mockEnabled) {
        this.mockEnabled = mockEnabled;
        log.info("BinanceWebSocketManager initialized, mockEnabled={}", mockEnabled);
    }

    /**
     * 设置 K 线数据回调
     * 
     * @param callback 回调函数 (symbolId, klineEvent)
     */
    public void setKlineCallback(BiConsumer<Long, BinanceWsKlineEvent> callback) {
        this.klineCallback = callback;
    }

    /**
     * 设置断线回调
     * 
     * @param callback 回调函数 (subscriptionKey)
     */
    public void setDisconnectCallback(java.util.function.Consumer<String> callback) {
        this.disconnectCallback = callback;
    }

    /**
     * 订阅 K 线数据
     * 
     * @param dataSource 数据源
     * @param symbol 交易对
     * @param interval 时间周期
     * @param proxyConfig 代理配置
     * @return 是否订阅成功
     */
    public boolean subscribe(DataSource dataSource, Symbol symbol, String interval, ProxyConfig proxyConfig) {
        String subscriptionKey = buildSubscriptionKey(dataSource.getId(), symbol.getId(), interval);

        lock.writeLock().lock();
        try {
            // 检查是否已订阅
            if (subscriptions.containsKey(subscriptionKey)) {
                log.debug("Already subscribed: {}", subscriptionKey);
                return true;
            }

            // 获取 WebSocket URL
            String wsUrl = getWsUrl(dataSource);

            // 创建 WebSocket 客户端
            BinanceWebSocketClient client = new BinanceWebSocketClient(
                    wsUrl,
                    symbol.getSymbol(),
                    interval,
                    proxyConfig,
                    event -> handleKlineEvent(symbol.getId(), event),
                    error -> handleError(subscriptionKey, error),
                    () -> handleDisconnect(subscriptionKey),
                    mockEnabled
            );

            // 连接
            client.connect();

            // 保存订阅信息
            SubscriptionInfo info = new SubscriptionInfo(
                    dataSource.getId(), symbol.getId(), symbol.getSymbol(), interval, client);
            subscriptions.put(subscriptionKey, info);

            // 更新索引
            subscriptionsByDataSource
                    .computeIfAbsent(dataSource.getId(), k -> ConcurrentHashMap.newKeySet())
                    .add(subscriptionKey);
            subscriptionsBySymbol
                    .computeIfAbsent(symbol.getId(), k -> ConcurrentHashMap.newKeySet())
                    .add(subscriptionKey);

            log.info("Subscribed to WebSocket: {}", subscriptionKey);
            return true;

        } catch (Exception e) {
            log.error("Failed to subscribe: {}, error={}", subscriptionKey, e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 取消订阅
     * 
     * @param dataSourceId 数据源 ID
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @return 是否取消成功
     */
    public boolean unsubscribe(Long dataSourceId, Long symbolId, String interval) {
        String subscriptionKey = buildSubscriptionKey(dataSourceId, symbolId, interval);

        lock.writeLock().lock();
        try {
            SubscriptionInfo info = subscriptions.remove(subscriptionKey);
            if (info == null) {
                log.debug("Subscription not found: {}", subscriptionKey);
                return false;
            }

            // 关闭 WebSocket 连接
            if (info.getClient() != null) {
                info.getClient().close();
            }

            // 更新索引
            Set<String> dsKeys = subscriptionsByDataSource.get(dataSourceId);
            if (dsKeys != null) {
                dsKeys.remove(subscriptionKey);
                if (dsKeys.isEmpty()) {
                    subscriptionsByDataSource.remove(dataSourceId);
                }
            }

            Set<String> symKeys = subscriptionsBySymbol.get(symbolId);
            if (symKeys != null) {
                symKeys.remove(subscriptionKey);
                if (symKeys.isEmpty()) {
                    subscriptionsBySymbol.remove(symbolId);
                }
            }

            log.info("Unsubscribed from WebSocket: {}", subscriptionKey);
            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 取消交易对的所有订阅
     * 
     * @param symbolId 交易对 ID
     * @return 取消的订阅数量
     */
    public int unsubscribeBySymbol(Long symbolId) {
        lock.writeLock().lock();
        try {
            Set<String> keys = subscriptionsBySymbol.get(symbolId);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (String key : new ArrayList<>(keys)) {
                SubscriptionInfo info = subscriptions.remove(key);
                if (info != null) {
                    if (info.getClient() != null) {
                        info.getClient().close();
                    }
                    count++;

                    // 更新数据源索引
                    Set<String> dsKeys = subscriptionsByDataSource.get(info.getDataSourceId());
                    if (dsKeys != null) {
                        dsKeys.remove(key);
                        if (dsKeys.isEmpty()) {
                            subscriptionsByDataSource.remove(info.getDataSourceId());
                        }
                    }
                }
            }

            subscriptionsBySymbol.remove(symbolId);
            log.info("Unsubscribed {} connections for symbolId={}", count, symbolId);
            return count;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 取消数据源的所有订阅
     * 
     * @param dataSourceId 数据源 ID
     * @return 取消的订阅数量
     */
    public int unsubscribeByDataSource(Long dataSourceId) {
        lock.writeLock().lock();
        try {
            Set<String> keys = subscriptionsByDataSource.get(dataSourceId);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }

            int count = 0;
            for (String key : new ArrayList<>(keys)) {
                SubscriptionInfo info = subscriptions.remove(key);
                if (info != null) {
                    if (info.getClient() != null) {
                        info.getClient().close();
                    }
                    count++;

                    // 更新交易对索引
                    Set<String> symKeys = subscriptionsBySymbol.get(info.getSymbolId());
                    if (symKeys != null) {
                        symKeys.remove(key);
                        if (symKeys.isEmpty()) {
                            subscriptionsBySymbol.remove(info.getSymbolId());
                        }
                    }
                }
            }

            subscriptionsByDataSource.remove(dataSourceId);
            log.info("Unsubscribed {} connections for dataSourceId={}", count, dataSourceId);
            return count;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 取消所有订阅
     * 
     * @return 取消的订阅数量
     */
    public int unsubscribeAll() {
        lock.writeLock().lock();
        try {
            int count = subscriptions.size();

            for (SubscriptionInfo info : subscriptions.values()) {
                if (info.getClient() != null) {
                    info.getClient().close();
                }
            }

            subscriptions.clear();
            subscriptionsByDataSource.clear();
            subscriptionsBySymbol.clear();

            log.info("Unsubscribed all {} connections", count);
            return count;

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取订阅信息
     * 
     * @param dataSourceId 数据源 ID
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @return 订阅信息
     */
    public SubscriptionInfo getSubscription(Long dataSourceId, Long symbolId, String interval) {
        String key = buildSubscriptionKey(dataSourceId, symbolId, interval);
        return subscriptions.get(key);
    }

    /**
     * 检查是否已订阅
     * 
     * @param dataSourceId 数据源 ID
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @return 是否已订阅
     */
    public boolean isSubscribed(Long dataSourceId, Long symbolId, String interval) {
        String key = buildSubscriptionKey(dataSourceId, symbolId, interval);
        return subscriptions.containsKey(key);
    }

    /**
     * 获取所有订阅
     * 
     * @return 订阅列表
     */
    public List<SubscriptionInfo> getAllSubscriptions() {
        return new ArrayList<>(subscriptions.values());
    }

    /**
     * 获取数据源的所有订阅
     * 
     * @param dataSourceId 数据源 ID
     * @return 订阅列表
     */
    public List<SubscriptionInfo> getSubscriptionsByDataSource(Long dataSourceId) {
        Set<String> keys = subscriptionsByDataSource.get(dataSourceId);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<SubscriptionInfo> result = new ArrayList<>();
        for (String key : keys) {
            SubscriptionInfo info = subscriptions.get(key);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 获取交易对的所有订阅
     * 
     * @param symbolId 交易对 ID
     * @return 订阅列表
     */
    public List<SubscriptionInfo> getSubscriptionsBySymbol(Long symbolId) {
        Set<String> keys = subscriptionsBySymbol.get(symbolId);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<SubscriptionInfo> result = new ArrayList<>();
        for (String key : keys) {
            SubscriptionInfo info = subscriptions.get(key);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 获取订阅数量
     * 
     * @return 订阅数量
     */
    public int getSubscriptionCount() {
        return subscriptions.size();
    }

    /**
     * 获取已连接的订阅数量
     * 
     * @return 已连接数量
     */
    public int getConnectedCount() {
        return (int) subscriptions.values().stream()
                .filter(SubscriptionInfo::isConnected)
                .count();
    }

    /**
     * 处理 K 线事件
     */
    private void handleKlineEvent(Long symbolId, BinanceWsKlineEvent event) {
        if (klineCallback != null) {
            try {
                klineCallback.accept(symbolId, event);
            } catch (Exception e) {
                log.error("Error in kline callback: symbolId={}, error={}", symbolId, e.getMessage());
            }
        }
    }

    /**
     * 处理错误
     */
    private void handleError(String subscriptionKey, Throwable error) {
        log.error("WebSocket error: {}, error={}", subscriptionKey, error.getMessage());
    }

    /**
     * 处理断开连接
     */
    private void handleDisconnect(String subscriptionKey) {
        log.warn("WebSocket disconnected: {}", subscriptionKey);
        if (disconnectCallback != null) {
            try {
                disconnectCallback.accept(subscriptionKey);
            } catch (Exception e) {
                log.error("Error in disconnect callback: {}, error={}", subscriptionKey, e.getMessage());
            }
        }
    }

    /**
     * 构建订阅键
     */
    private String buildSubscriptionKey(Long dataSourceId, Long symbolId, String interval) {
        return dataSourceId + "_" + symbolId + "_" + interval;
    }

    /**
     * 获取 WebSocket URL
     */
    private String getWsUrl(DataSource dataSource) {
        // 优先使用数据源配置的 WebSocket URL
        if (dataSource.getWsUrl() != null && !dataSource.getWsUrl().isBlank()) {
            return dataSource.getWsUrl();
        }

        // 根据 baseUrl 推断 WebSocket URL
        String baseUrl = dataSource.getBaseUrl();
        if (baseUrl != null) {
            if (baseUrl.contains("fapi.binance.com")) {
                return BinanceWebSocketClient.DEFAULT_FUTURES_USDT_WS_URL;
            } else if (baseUrl.contains("dapi.binance.com")) {
                return BinanceWebSocketClient.DEFAULT_FUTURES_COIN_WS_URL;
            }
        }

        return BinanceWebSocketClient.DEFAULT_SPOT_WS_URL;
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        log.info("Shutting down WebSocket manager");
        unsubscribeAll();
    }
}

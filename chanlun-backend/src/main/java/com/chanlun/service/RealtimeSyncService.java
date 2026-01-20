package com.chanlun.service;

import com.chanlun.config.ProxyConfig;
import com.chanlun.entity.*;
import com.chanlun.event.RealtimeSyncConfigChangedEvent;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.BinanceWebSocketManager;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.exchange.model.BinanceWsKlineEvent;
import com.chanlun.util.EncryptUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 实时数据同步服务
 * 
 * 提供 WebSocket 实时 K 线数据同步功能：
 * - 管理 WebSocket 连接
 * - 处理 K 线数据写入
 * - 断线期间数据补充
 * - 更新 sync_status
 * - 响应全局开关变化
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimeSyncService {

    private final SymbolService symbolService;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final KlineService klineService;
    private final SyncService syncService;
    private final SystemConfigService systemConfigService;
    private final BinanceClientFactory binanceClientFactory;
    private final EncryptUtil encryptUtil;

    @Value("${app.exchange.api-mock:false}")
    private boolean mockEnabled;

    /**
     * WebSocket 管理器
     */
    private BinanceWebSocketManager webSocketManager;

    /**
     * 断线时间记录 (key: subscriptionKey, value: disconnectTime)
     */
    private final Map<String, Instant> disconnectTimes = new ConcurrentHashMap<>();

    /**
     * 数据补充执行器
     */
    private ScheduledExecutorService gapFillExecutor;

    /**
     * 数据补充队列
     */
    private final BlockingQueue<GapFillTask> gapFillQueue = new LinkedBlockingQueue<>();

    /**
     * 支持的 K 线周期
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 数据补充任务
     */
    private record GapFillTask(Long dataSourceId, Long symbolId, String symbolCode, 
                               String interval, Instant startTime, Instant endTime) {}

    /**
     * 初始化
     */
    @PostConstruct
    public void init() {
        webSocketManager = new BinanceWebSocketManager(mockEnabled);
        webSocketManager.setKlineCallback(this::handleKlineEvent);
        webSocketManager.setDisconnectCallback(this::handleDisconnect);

        // 启动数据补充处理线程
        gapFillExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "realtime-gap-fill"));
        gapFillExecutor.scheduleWithFixedDelay(
                this::processGapFillQueue, 5, 1, TimeUnit.SECONDS);

        log.info("RealtimeSyncService initialized");
    }

    /**
     * 销毁
     */
    @PreDestroy
    public void destroy() {
        if (webSocketManager != null) {
            webSocketManager.shutdown();
        }
        if (gapFillExecutor != null) {
            gapFillExecutor.shutdown();
        }
        log.info("RealtimeSyncService destroyed");
    }

    /**
     * 启动交易对的实时同步
     * 
     * @param symbolId 交易对 ID
     * @return 启动的订阅数量
     */
    public int startRealtimeSync(Long symbolId) {
        // 检查全局开关
        if (!systemConfigService.isRealtimeSyncEnabled()) {
            log.warn("Realtime sync is disabled globally");
            return 0;
        }

        Symbol symbol = symbolService.findById(symbolId);
        if (symbol == null) {
            log.warn("Symbol not found: {}", symbolId);
            return 0;
        }

        // 检查交易对实时同步开关
        if (!Boolean.TRUE.equals(symbol.getRealtimeSyncEnabled())) {
            log.debug("Realtime sync not enabled for symbol: {}", symbol.getSymbol());
            return 0;
        }

        // 获取市场和数据源
        Market market = marketService.findById(symbol.getMarketId());
        if (market == null || !Boolean.TRUE.equals(market.getEnabled())) {
            log.warn("Market not found or disabled for symbol: {}", symbol.getSymbol());
            return 0;
        }

        DataSource dataSource = dataSourceService.findById(market.getDataSourceId());
        if (dataSource == null || !Boolean.TRUE.equals(dataSource.getEnabled())) {
            log.warn("DataSource not found or disabled for symbol: {}", symbol.getSymbol());
            return 0;
        }

        // 获取同步周期
        List<String> intervals = parseIntervals(symbol.getSyncIntervals());
        if (intervals.isEmpty()) {
            log.debug("No sync intervals configured for symbol: {}", symbol.getSymbol());
            return 0;
        }

        // 创建代理配置
        ProxyConfig proxyConfig = createProxyConfig(dataSource);

        // 订阅每个周期
        int count = 0;
        for (String interval : intervals) {
            if (webSocketManager.subscribe(dataSource, symbol, interval, proxyConfig)) {
                count++;
            }
        }

        log.info("Started realtime sync for symbol {}: {} subscriptions", symbol.getSymbol(), count);
        return count;
    }

    /**
     * 停止交易对的实时同步
     * 
     * @param symbolId 交易对 ID
     * @return 停止的订阅数量
     */
    public int stopRealtimeSync(Long symbolId) {
        int count = webSocketManager.unsubscribeBySymbol(symbolId);
        log.info("Stopped realtime sync for symbolId={}: {} subscriptions", symbolId, count);
        return count;
    }

    /**
     * 停止数据源的所有实时同步
     * 
     * @param dataSourceId 数据源 ID
     * @return 停止的订阅数量
     */
    public int stopRealtimeSyncByDataSource(Long dataSourceId) {
        int count = webSocketManager.unsubscribeByDataSource(dataSourceId);
        log.info("Stopped realtime sync for dataSourceId={}: {} subscriptions", dataSourceId, count);
        return count;
    }

    /**
     * 停止所有实时同步
     * 
     * @return 停止的订阅数量
     */
    public int stopAllRealtimeSync() {
        int count = webSocketManager.unsubscribeAll();
        log.info("Stopped all realtime sync: {} subscriptions", count);
        return count;
    }

    /**
     * 启动所有启用实时同步的交易对
     * 
     * @return 启动的订阅数量
     */
    public int startAllRealtimeSync() {
        // 检查全局开关
        if (!systemConfigService.isRealtimeSyncEnabled()) {
            log.warn("Realtime sync is disabled globally, not starting any subscriptions");
            return 0;
        }

        List<Symbol> symbols = symbolService.getRealtimeSyncEnabledSymbols();
        int totalCount = 0;

        for (Symbol symbol : symbols) {
            try {
                int count = startRealtimeSync(symbol.getId());
                totalCount += count;
            } catch (Exception e) {
                log.error("Failed to start realtime sync for symbol {}: {}", 
                        symbol.getSymbol(), e.getMessage());
            }
        }

        log.info("Started realtime sync for all enabled symbols: {} subscriptions", totalCount);
        return totalCount;
    }

    /**
     * 处理 K 线事件
     */
    private void handleKlineEvent(Long symbolId, BinanceWsKlineEvent event) {
        if (event == null || event.getKline() == null) {
            return;
        }

        BinanceWsKlineEvent.KlineData klineData = event.getKline();

        // 只处理已完结的 K 线
        if (!Boolean.TRUE.equals(klineData.getClosed())) {
            return;
        }

        try {
            // 转换为 Kline 实体
            Kline kline = convertToKline(symbolId, klineData);

            // 保存 K 线数据
            boolean saved = klineService.upsert(kline);

            if (saved) {
                // 更新 sync_status
                updateSyncStatusAfterRealtimeSync(symbolId, klineData.getInterval(), 
                        kline.getOpenTime(), 1);

                log.debug("Saved realtime kline: symbolId={}, interval={}, openTime={}",
                        symbolId, klineData.getInterval(), kline.getOpenTime());
            }

        } catch (Exception e) {
            log.error("Failed to process kline event: symbolId={}, error={}", 
                    symbolId, e.getMessage());
        }
    }

    /**
     * 处理断开连接
     */
    private void handleDisconnect(String subscriptionKey) {
        // 记录断线时间
        disconnectTimes.put(subscriptionKey, Instant.now());
        log.warn("WebSocket disconnected, recorded disconnect time: {}", subscriptionKey);
    }

    /**
     * 处理重新连接后的数据补充
     * 
     * @param dataSourceId 数据源 ID
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     */
    public void fillGapAfterReconnect(Long dataSourceId, Long symbolId, String interval) {
        String subscriptionKey = dataSourceId + "_" + symbolId + "_" + interval;
        Instant disconnectTime = disconnectTimes.remove(subscriptionKey);

        if (disconnectTime == null) {
            log.debug("No disconnect time recorded for {}", subscriptionKey);
            return;
        }

        Instant now = Instant.now();
        Duration gap = Duration.between(disconnectTime, now);

        // 如果断线时间超过 1 分钟，需要补充数据
        if (gap.toMinutes() >= 1) {
            Symbol symbol = symbolService.findById(symbolId);
            if (symbol != null) {
                GapFillTask task = new GapFillTask(
                        dataSourceId, symbolId, symbol.getSymbol(), 
                        interval, disconnectTime, now);
                gapFillQueue.offer(task);
                log.info("Queued gap fill task: {}, gap={}s", subscriptionKey, gap.toSeconds());
            }
        }
    }

    /**
     * 处理数据补充队列
     */
    private void processGapFillQueue() {
        GapFillTask task;
        while ((task = gapFillQueue.poll()) != null) {
            try {
                fillGap(task);
            } catch (Exception e) {
                log.error("Failed to fill gap: {}, error={}", task, e.getMessage());
            }
        }
    }

    /**
     * 执行数据补充
     */
    private void fillGap(GapFillTask task) {
        log.info("Filling gap: symbolId={}, interval={}, from {} to {}",
                task.symbolId(), task.interval(), task.startTime(), task.endTime());

        try {
            // 获取数据源
            DataSource dataSource = dataSourceService.findById(task.dataSourceId());
            if (dataSource == null || !Boolean.TRUE.equals(dataSource.getEnabled())) {
                log.warn("DataSource not found or disabled: {}", task.dataSourceId());
                return;
            }

            // 创建币安客户端
            BinanceClient client = binanceClientFactory.createClient(dataSource);

            try {
                // 调用 REST API 获取缺失的 K 线数据
                BinanceApiResponse<List<BinanceKline>> response = client.getKlines(
                        task.symbolCode(), task.interval(), 
                        task.startTime(), task.endTime(), 1000);

                if (!response.isSuccess()) {
                    log.error("Failed to get klines for gap fill: {}", response.getMessage());
                    return;
                }

                List<BinanceKline> klines = response.getData();
                if (klines == null || klines.isEmpty()) {
                    log.debug("No klines to fill for gap");
                    return;
                }

                // 转换并保存
                List<Kline> entities = new ArrayList<>(klines.size());
                for (BinanceKline bk : klines) {
                    entities.add(convertBinanceKlineToEntity(task.symbolId(), task.interval(), bk));
                }

                int saved = klineService.batchUpsert(entities);

                // 更新 sync_status
                if (saved > 0) {
                    Instant lastKlineTime = klines.get(klines.size() - 1).getOpenTimeInstant();
                    updateSyncStatusAfterRealtimeSync(task.symbolId(), task.interval(), 
                            lastKlineTime, saved);
                }

                log.info("Gap filled: symbolId={}, interval={}, saved={} klines",
                        task.symbolId(), task.interval(), saved);

            } finally {
                client.close();
            }

        } catch (Exception e) {
            log.error("Error filling gap: {}", e.getMessage());
        }
    }

    /**
     * 转换 WebSocket K 线数据为实体
     */
    private Kline convertToKline(Long symbolId, BinanceWsKlineEvent.KlineData klineData) {
        return Kline.builder()
                .symbolId(symbolId)
                .interval(klineData.getInterval())
                .openTime(klineData.getOpenTimeInstant())
                .open(klineData.getOpen())
                .high(klineData.getHigh())
                .low(klineData.getLow())
                .close(klineData.getClose())
                .volume(klineData.getVolume())
                .quoteVolume(klineData.getQuoteVolume())
                .trades(klineData.getTrades())
                .closeTime(klineData.getCloseTimeInstant())
                .build();
    }

    /**
     * 转换币安 K 线为实体
     */
    private Kline convertBinanceKlineToEntity(Long symbolId, String interval, BinanceKline bk) {
        return Kline.builder()
                .symbolId(symbolId)
                .interval(interval)
                .openTime(bk.getOpenTimeInstant())
                .open(bk.getOpen())
                .high(bk.getHigh())
                .low(bk.getLow())
                .close(bk.getClose())
                .volume(bk.getVolume())
                .quoteVolume(bk.getQuoteVolume())
                .trades(bk.getTrades())
                .closeTime(bk.getCloseTimeInstant())
                .build();
    }

    /**
     * 实时同步后更新 sync_status
     */
    @Transactional
    public void updateSyncStatusAfterRealtimeSync(Long symbolId, String interval, 
                                                   Instant lastKlineTime, int syncedCount) {
        syncService.updateSyncStatus(symbolId, interval, lastKlineTime, syncedCount);
    }

    /**
     * 解析同步周期字符串
     */
    private List<String> parseIntervals(String syncIntervals) {
        if (syncIntervals == null || syncIntervals.isBlank()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (String interval : syncIntervals.split(",")) {
            String trimmed = interval.trim();
            if (VALID_INTERVALS.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 创建代理配置
     */
    private ProxyConfig createProxyConfig(DataSource dataSource) {
        if (!Boolean.TRUE.equals(dataSource.getProxyEnabled())) {
            return null;
        }

        String decryptedPassword = null;
        if (dataSource.getProxyPassword() != null && !dataSource.getProxyPassword().isBlank()) {
            try {
                decryptedPassword = encryptUtil.decrypt(dataSource.getProxyPassword());
            } catch (Exception e) {
                log.warn("Failed to decrypt proxy password: {}", e.getMessage());
            }
        }

        return ProxyConfig.fromDataSource(dataSource, decryptedPassword);
    }

    /**
     * 获取订阅数量
     */
    public int getSubscriptionCount() {
        return webSocketManager.getSubscriptionCount();
    }

    /**
     * 获取已连接的订阅数量
     */
    public int getConnectedCount() {
        return webSocketManager.getConnectedCount();
    }

    /**
     * 获取所有订阅信息
     */
    public List<BinanceWebSocketManager.SubscriptionInfo> getAllSubscriptions() {
        return webSocketManager.getAllSubscriptions();
    }

    /**
     * 检查是否已订阅
     */
    public boolean isSubscribed(Long dataSourceId, Long symbolId, String interval) {
        return webSocketManager.isSubscribed(dataSourceId, symbolId, interval);
    }

    /**
     * 监听实时同步配置变更事件
     * 
     * 当 sync.realtime.enabled 设为 false 时，断开所有 WebSocket 订阅
     * 当 sync.realtime.enabled 设为 true 时，启动所有启用实时同步的交易对
     */
    @EventListener
    public void onRealtimeSyncConfigChanged(RealtimeSyncConfigChangedEvent event) {
        onRealtimeSyncEnabledChanged(event.isEnabled());
    }

    /**
     * 处理全局实时同步开关变化
     * 
     * 当 sync.realtime.enabled 设为 false 时，断开所有 WebSocket 订阅
     */
    public void onRealtimeSyncEnabledChanged(boolean enabled) {
        if (!enabled) {
            log.info("Realtime sync disabled globally, disconnecting all WebSocket subscriptions");
            stopAllRealtimeSync();
        } else {
            log.info("Realtime sync enabled globally, starting subscriptions for enabled symbols");
            startAllRealtimeSync();
        }
    }
}

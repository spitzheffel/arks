package com.chanlun.service;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 历史数据同步服务
 * 
 * 提供历史 K 线数据的批量拉取功能：
 * - 分段处理，单次跨度不超过 30 天
 * - 支持手动触发和定时增量同步
 * - 首次启用仅补前一日数据
 * - 同步成功后更新 sync_status
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistorySyncService {

    private final SymbolService symbolService;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final KlineService klineService;
    private final SyncService syncService;
    private final BinanceClientFactory binanceClientFactory;

    /**
     * 单次同步最大时间跨度（30 天）
     */
    private static final Duration MAX_SYNC_DURATION = Duration.ofDays(30);

    /**
     * 单次 API 请求最大返回数量
     */
    private static final int MAX_KLINES_PER_REQUEST = 1000;

    /**
     * API 请求间隔（毫秒），防止限流
     */
    private static final long REQUEST_INTERVAL_MS = 200;

    /**
     * 支持的 K 线周期列表
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 执行历史数据同步
     * 
     * 分段拉取指定时间范围的 K 线数据，单次跨度不超过 30 天
     * 
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 同步的 K 线数量
     */
    @Transactional
    public int syncHistory(Long symbolId, String interval, Instant startTime, Instant endTime) {
        // 校验参数
        validateSyncParams(symbolId, interval, startTime, endTime);
        
        // 获取交易对信息
        Symbol symbol = symbolService.findById(symbolId);
        Market market = marketService.findById(symbol.getMarketId());
        DataSource dataSource = dataSourceService.findById(market.getDataSourceId());
        
        // 校验数据源和市场状态
        validateDataSourceAndMarket(dataSource, market);
        
        // 创建同步任务
        SyncTask task = syncService.createHistoryTask(symbolId, interval, startTime, endTime);
        
        try {
            // 开始任务
            syncService.startTask(task.getId());
            
            // 创建币安客户端
            BinanceClient client = binanceClientFactory.createClient(dataSource);
            
            try {
                // 分段同步
                int totalSynced = syncInSegments(client, symbol.getSymbol(), symbolId, 
                        interval, startTime, endTime, task.getId());
                
                // 完成任务
                syncService.completeTask(task.getId(), totalSynced);
                
                // 更新同步状态
                updateSyncStatusAfterSync(symbolId, interval, totalSynced);
                
                log.info("History sync completed: symbolId={}, interval={}, synced={}", 
                        symbolId, interval, totalSynced);
                
                return totalSynced;
                
            } finally {
                client.close();
            }
            
        } catch (Exception e) {
            // 任务失败
            syncService.failTask(task.getId(), e.getMessage());
            log.error("History sync failed: symbolId={}, interval={}, error={}", 
                    symbolId, interval, e.getMessage());
            throw new BusinessException("历史数据同步失败: " + e.getMessage());
        }
    }

    /**
     * 分段同步 K 线数据
     * 
     * @param client 币安客户端
     * @param symbolCode 交易对代码
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param taskId 任务 ID
     * @return 同步的 K 线数量
     */
    private int syncInSegments(BinanceClient client, String symbolCode, Long symbolId,
                                String interval, Instant startTime, Instant endTime, Long taskId) {
        int totalSynced = 0;
        Instant segmentStart = startTime;
        
        while (segmentStart.isBefore(endTime)) {
            // 计算分段结束时间（不超过 30 天）
            Instant segmentEnd = segmentStart.plus(MAX_SYNC_DURATION);
            if (segmentEnd.isAfter(endTime)) {
                segmentEnd = endTime;
            }
            
            log.debug("Syncing segment: {} to {}", segmentStart, segmentEnd);
            
            // 同步当前分段
            int segmentSynced = syncSegment(client, symbolCode, symbolId, interval, 
                    segmentStart, segmentEnd);
            totalSynced += segmentSynced;
            
            // 更新任务进度
            syncService.updateSyncedCount(taskId, totalSynced);
            
            // 移动到下一个分段
            segmentStart = segmentEnd;
            
            // 防止限流
            sleepBetweenRequests();
        }
        
        return totalSynced;
    }

    /**
     * 同步单个分段的 K 线数据
     * 
     * @param client 币安客户端
     * @param symbolCode 交易对代码
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @param startTime 分段开始时间
     * @param endTime 分段结束时间
     * @return 同步的 K 线数量
     */
    private int syncSegment(BinanceClient client, String symbolCode, Long symbolId,
                            String interval, Instant startTime, Instant endTime) {
        int segmentSynced = 0;
        Instant currentStart = startTime;
        
        while (currentStart.isBefore(endTime)) {
            // 调用 API 获取 K 线数据
            BinanceApiResponse<List<BinanceKline>> response = client.getKlines(
                    symbolCode, interval, currentStart, endTime, MAX_KLINES_PER_REQUEST);
            
            if (!response.isSuccess()) {
                throw new BusinessException("获取 K 线数据失败: " + response.getMessage());
            }
            
            List<BinanceKline> klines = response.getData();
            if (klines == null || klines.isEmpty()) {
                break;
            }
            
            // 转换并保存 K 线数据
            List<Kline> entities = convertToEntities(klines, symbolId, interval);
            int saved = klineService.batchUpsert(entities);
            segmentSynced += saved;
            
            // 如果返回数量小于请求数量，说明已经没有更多数据
            if (klines.size() < MAX_KLINES_PER_REQUEST) {
                break;
            }
            
            // 更新下一次请求的开始时间（最后一根 K 线的开盘时间 + 1ms）
            BinanceKline lastKline = klines.get(klines.size() - 1);
            currentStart = Instant.ofEpochMilli(lastKline.getOpenTime() + 1);
            
            // 防止限流
            sleepBetweenRequests();
        }
        
        return segmentSynced;
    }

    /**
     * 将币安 K 线数据转换为实体
     */
    private List<Kline> convertToEntities(List<BinanceKline> binanceKlines, Long symbolId, String interval) {
        List<Kline> entities = new ArrayList<>(binanceKlines.size());
        
        for (BinanceKline bk : binanceKlines) {
            Kline kline = Kline.builder()
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
            entities.add(kline);
        }
        
        return entities;
    }

    /**
     * 同步成功后更新 sync_status
     */
    private void updateSyncStatusAfterSync(Long symbolId, String interval, int syncedCount) {
        if (syncedCount <= 0) {
            return;
        }
        
        // 获取最新的 K 线时间
        Instant lastKlineTime = klineService.getMaxOpenTime(symbolId, interval);
        
        // 更新同步状态
        syncService.updateSyncStatus(symbolId, interval, lastKlineTime, syncedCount);
    }

    /**
     * 校验同步参数
     */
    private void validateSyncParams(Long symbolId, String interval, Instant startTime, Instant endTime) {
        if (symbolId == null) {
            throw new BusinessException("交易对 ID 不能为空");
        }
        if (interval == null || interval.isBlank()) {
            throw new BusinessException("时间周期不能为空");
        }
        if (!VALID_INTERVALS.contains(interval)) {
            throw new BusinessException("不支持的时间周期: " + interval);
        }
        if (startTime == null) {
            throw new BusinessException("开始时间不能为空");
        }
        if (endTime == null) {
            throw new BusinessException("结束时间不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
        if (startTime.isAfter(Instant.now())) {
            throw new BusinessException("开始时间不能晚于当前时间");
        }
    }

    /**
     * 校验数据源和市场状态
     */
    private void validateDataSourceAndMarket(DataSource dataSource, Market market) {
        if (!dataSource.getEnabled()) {
            throw new BusinessException("数据源未启用");
        }
        if (!market.getEnabled()) {
            throw new BusinessException("市场未启用");
        }
        if (!"BINANCE".equalsIgnoreCase(dataSource.getExchangeType())) {
            throw new BusinessException("仅支持币安数据源");
        }
    }

    /**
     * 请求间隔休眠
     */
    private void sleepBetweenRequests() {
        try {
            Thread.sleep(REQUEST_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("同步被中断");
        }
    }

    /**
     * 获取周期对应的毫秒数
     */
    public long getIntervalMillis(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "3m" -> 3 * 60_000L;
            case "5m" -> 5 * 60_000L;
            case "15m" -> 15 * 60_000L;
            case "30m" -> 30 * 60_000L;
            case "1h" -> 60 * 60_000L;
            case "2h" -> 2 * 60 * 60_000L;
            case "4h" -> 4 * 60 * 60_000L;
            case "6h" -> 6 * 60 * 60_000L;
            case "8h" -> 8 * 60 * 60_000L;
            case "12h" -> 12 * 60 * 60_000L;
            case "1d" -> 24 * 60 * 60_000L;
            case "3d" -> 3 * 24 * 60 * 60_000L;
            case "1w" -> 7 * 24 * 60 * 60_000L;
            case "1M" -> 30 * 24 * 60 * 60_000L;
            default -> 60_000L;
        };
    }

    /**
     * 执行增量同步
     * 
     * 基于 sync_status.last_kline_time 追赶到当前时间。
     * - 若 last_kline_time 为 NULL（首次启用），仅补前一日数据
     * - 若跨度超过 30 天，分段追赶
     * 
     * @param symbolId 交易对 ID
     * @param interval 时间周期
     * @return 同步的 K 线数量
     */
    @Transactional
    public int syncIncremental(Long symbolId, String interval) {
        // 获取同步状态
        SyncStatus status = syncService.getSyncStatus(symbolId, interval);
        
        Instant startTime;
        Instant endTime = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        
        if (status == null || status.getLastKlineTime() == null) {
            // 首次启用，仅补前一日数据
            startTime = endTime.minus(Duration.ofDays(1));
            log.info("First time sync for symbolId={}, interval={}, syncing last 1 day", symbolId, interval);
        } else {
            // 从最后 K 线时间的下一根开始
            long intervalMs = getIntervalMillis(interval);
            startTime = status.getLastKlineTime().plusMillis(intervalMs);
            
            // 如果已经是最新的，不需要同步
            if (!startTime.isBefore(endTime)) {
                log.debug("Already up to date: symbolId={}, interval={}", symbolId, interval);
                return 0;
            }
            
            log.info("Incremental sync for symbolId={}, interval={}, from {} to {}", 
                    symbolId, interval, startTime, endTime);
        }
        
        // 执行同步
        return syncHistory(symbolId, interval, startTime, endTime);
    }

    /**
     * 执行所有启用历史同步的交易对的增量同步
     * 
     * 仅处理：
     * - 未删除且启用的数据源
     * - 启用的市场
     * - 启用历史同步的交易对
     * - 交易对配置的同步周期
     * 
     * @return 同步结果摘要
     */
    public IncrementalSyncSummary syncAllIncremental() {
        log.info("Starting incremental sync for all enabled symbols");
        
        IncrementalSyncSummary summary = new IncrementalSyncSummary();
        
        // 获取所有启用历史同步的交易对
        List<SymbolDTO> symbols = symbolService.getHistorySyncEnabled();
        
        for (SymbolDTO symbolDTO : symbols) {
            try {
                // 获取交易对配置的同步周期
                List<String> intervals = symbolDTO.getSyncIntervals();
                if (intervals == null || intervals.isEmpty()) {
                    log.debug("No sync intervals configured for symbol: {}", symbolDTO.getSymbol());
                    continue;
                }
                
                // 对每个周期执行增量同步
                for (String interval : intervals) {
                    try {
                        int synced = syncIncremental(symbolDTO.getId(), interval);
                        summary.addSuccess(symbolDTO.getId(), interval, synced);
                    } catch (Exception e) {
                        log.error("Incremental sync failed for symbol={}, interval={}: {}", 
                                symbolDTO.getSymbol(), interval, e.getMessage());
                        summary.addFailure(symbolDTO.getId(), interval, e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to process symbol {}: {}", symbolDTO.getSymbol(), e.getMessage());
            }
        }
        
        log.info("Incremental sync completed: {} symbols processed, {} succeeded, {} failed, {} klines synced",
                summary.getTotalSymbols(), summary.getSuccessCount(), summary.getFailureCount(), summary.getTotalKlines());
        
        return summary;
    }

    /**
     * 增量同步结果摘要
     */
    public static class IncrementalSyncSummary {
        private int totalSymbols = 0;
        private int successCount = 0;
        private int failureCount = 0;
        private int totalKlines = 0;
        private final List<SyncResult> results = new ArrayList<>();

        public void addSuccess(Long symbolId, String interval, int klines) {
            totalSymbols++;
            successCount++;
            totalKlines += klines;
            results.add(new SyncResult(symbolId, interval, true, klines, null));
        }

        public void addFailure(Long symbolId, String interval, String error) {
            totalSymbols++;
            failureCount++;
            results.add(new SyncResult(symbolId, interval, false, 0, error));
        }

        public int getTotalSymbols() { return totalSymbols; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getTotalKlines() { return totalKlines; }
        public List<SyncResult> getResults() { return results; }

        public record SyncResult(Long symbolId, String interval, boolean success, int klines, String error) {}
    }
}

package com.chanlun.service;

import com.chanlun.dto.DataGapDTO;
import com.chanlun.entity.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.exchange.BinanceClient;
import com.chanlun.exchange.BinanceClientFactory;
import com.chanlun.exchange.model.BinanceApiResponse;
import com.chanlun.exchange.model.BinanceKline;
import com.chanlun.mapper.DataGapMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 缺口回补服务
 * 
 * 提供数据缺口的回补功能：
 * - 单个缺口回补
 * - 批量缺口回补（带限流）
 * - 自动缺口回补（检查全局和周期级开关）
 * - 回补状态流转
 * - 回补重试机制
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GapFillService {

    private final DataGapMapper dataGapMapper;
    private final DataGapService dataGapService;
    private final SymbolService symbolService;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final KlineService klineService;
    private final SyncService syncService;
    private final SystemConfigService systemConfigService;
    private final BinanceClientFactory binanceClientFactory;

    /**
     * 单次 API 请求最大返回数量
     */
    private static final int MAX_KLINES_PER_REQUEST = 1000;

    /**
     * API 请求间隔（毫秒），防止限流
     */
    private static final long REQUEST_INTERVAL_MS = 200;

    /**
     * 回补单个缺口
     * 
     * 流程：
     * 1. 校验缺口状态（只有 PENDING 状态可以回补）
     * 2. 更新状态为 FILLING
     * 3. 创建 sync_task 记录（task_type=GAP_FILL）
     * 4. 从交易所拉取缺口时间范围内的 K 线数据
     * 5. 保存 K 线数据
     * 6. 更新缺口状态为 FILLED
     * 7. 更新 sync_status
     * 
     * @param gapId 缺口ID
     * @return 回补结果
     */
    @Transactional
    public GapFillResult fillGap(Long gapId) {
        // 获取缺口信息
        DataGap gap = dataGapService.findById(gapId);
        
        // 校验缺口状态
        validateGapForFill(gap);
        
        // 获取交易对信息
        Symbol symbol = symbolService.findById(gap.getSymbolId());
        Market market = marketService.findById(symbol.getMarketId());
        DataSource dataSource = dataSourceService.findById(market.getDataSourceId());
        
        // 校验数据源和市场状态
        validateDataSourceAndMarket(dataSource, market);
        
        // 更新缺口状态为 FILLING
        dataGapService.updateStatus(gapId, DataGapService.STATUS_FILLING);
        
        // 创建 sync_task 记录
        SyncTask task = syncService.createGapFillTask(
                gap.getSymbolId(), 
                gap.getInterval(), 
                gap.getGapStart(), 
                gap.getGapEnd());
        
        try {
            // 开始任务
            syncService.startTask(task.getId());
            
            // 创建币安客户端
            BinanceClient client = binanceClientFactory.createClient(dataSource);
            
            try {
                // 拉取缺口时间范围内的 K 线数据
                int syncedCount = fetchAndSaveKlines(client, symbol.getSymbol(), 
                        gap.getSymbolId(), gap.getInterval(), gap.getGapStart(), gap.getGapEnd());
                
                // 完成任务
                syncService.completeTask(task.getId(), syncedCount);
                
                // 更新缺口状态为 FILLED
                dataGapService.updateStatus(gapId, DataGapService.STATUS_FILLED);
                
                // 更新 sync_status
                updateSyncStatusAfterFill(gap.getSymbolId(), gap.getInterval(), syncedCount);
                
                log.info("Gap fill completed: gapId={}, symbolId={}, interval={}, synced={}", 
                        gapId, gap.getSymbolId(), gap.getInterval(), syncedCount);
                
                return GapFillResult.success(gapId, syncedCount, "缺口回补成功");
                
            } finally {
                client.close();
            }
            
        } catch (Exception e) {
            // 任务失败
            syncService.failTask(task.getId(), e.getMessage());
            
            // 处理回补失败
            handleFillFailure(gap, e.getMessage());
            
            log.error("Gap fill failed: gapId={}, error={}", gapId, e.getMessage());
            return GapFillResult.failure(gapId, "缺口回补失败: " + e.getMessage());
        }
    }


    /**
     * 批量回补缺口（带限流）
     * 
     * 按照配置的批量大小和间隔时间进行回补，防止 API 限流
     * 
     * @param gapIds 缺口ID列表
     * @return 批量回补结果
     */
    public BatchGapFillResult batchFillGaps(List<Long> gapIds) {
        if (gapIds == null || gapIds.isEmpty()) {
            return BatchGapFillResult.empty();
        }
        
        // 获取配置
        long intervalMs = systemConfigService.getGapFillIntervalMs();
        
        BatchGapFillResult result = new BatchGapFillResult();
        
        for (Long gapId : gapIds) {
            try {
                GapFillResult fillResult = fillGap(gapId);
                
                if (fillResult.isSuccess()) {
                    result.addSuccess(fillResult);
                } else {
                    result.addFailure(fillResult);
                }
                
                // 防止限流，等待指定间隔
                sleepBetweenRequests(intervalMs);
                
            } catch (Exception e) {
                log.error("Batch fill gap failed: gapId={}, error={}", gapId, e.getMessage());
                result.addFailure(GapFillResult.failure(gapId, e.getMessage()));
            }
        }
        
        log.info("Batch gap fill completed: total={}, success={}, failed={}", 
                result.getTotalCount(), result.getSuccessCount(), result.getFailureCount());
        
        return result;
    }

    /**
     * 自动回补缺口
     * 
     * 检查全局开关和周期级开关，只回补符合条件的缺口
     * 
     * @return 自动回补结果
     */
    public BatchGapFillResult autoFillGaps() {
        // 检查全局自动回补开关
        if (!systemConfigService.isAutoGapFillEnabled()) {
            log.debug("Auto gap fill is disabled globally");
            return BatchGapFillResult.disabled("全局自动回补开关已关闭");
        }
        
        // 获取配置
        int batchSize = systemConfigService.getGapFillBatchSize();
        long intervalMs = systemConfigService.getGapFillIntervalMs();
        
        // 获取待回补的缺口
        List<DataGap> pendingGaps = dataGapMapper.selectPendingWithLimit(batchSize);
        
        if (pendingGaps.isEmpty()) {
            log.debug("No pending gaps to fill");
            return BatchGapFillResult.empty();
        }
        
        BatchGapFillResult result = new BatchGapFillResult();
        
        for (DataGap gap : pendingGaps) {
            try {
                // 检查周期级自动回补开关
                if (!isAutoGapFillEnabledForInterval(gap.getSymbolId(), gap.getInterval())) {
                    log.debug("Auto gap fill disabled for symbolId={}, interval={}", 
                            gap.getSymbolId(), gap.getInterval());
                    result.addSkipped(gap.getId(), "周期级自动回补开关已关闭");
                    continue;
                }
                
                // 执行回补
                GapFillResult fillResult = fillGap(gap.getId());
                
                if (fillResult.isSuccess()) {
                    result.addSuccess(fillResult);
                } else {
                    result.addFailure(fillResult);
                }
                
                // 防止限流
                sleepBetweenRequests(intervalMs);
                
            } catch (Exception e) {
                log.error("Auto fill gap failed: gapId={}, error={}", gap.getId(), e.getMessage());
                result.addFailure(GapFillResult.failure(gap.getId(), e.getMessage()));
            }
        }
        
        log.info("Auto gap fill completed: total={}, success={}, failed={}, skipped={}", 
                result.getTotalCount(), result.getSuccessCount(), result.getFailureCount(), result.getSkippedCount());
        
        return result;
    }

    /**
     * 检查指定交易对和周期的自动回补开关是否启用
     */
    private boolean isAutoGapFillEnabledForInterval(Long symbolId, String interval) {
        SyncStatus status = syncService.getSyncStatus(symbolId, interval);
        return status != null && Boolean.TRUE.equals(status.getAutoGapFillEnabled());
    }

    /**
     * 校验缺口是否可以回补
     */
    private void validateGapForFill(DataGap gap) {
        String status = gap.getStatus();
        
        // 只有 PENDING 状态可以回补
        if (!DataGapService.STATUS_PENDING.equals(status)) {
            if (DataGapService.STATUS_FILLED.equals(status)) {
                throw new BusinessException("缺口已回补完成，无需重复回补");
            }
            if (DataGapService.STATUS_FILLING.equals(status)) {
                throw new BusinessException("缺口正在回补中，请稍后再试");
            }
            if (DataGapService.STATUS_FAILED.equals(status)) {
                throw new BusinessException("缺口回补失败，请先重置状态后再试");
            }
            throw new BusinessException("缺口状态无效: " + status);
        }
    }

    /**
     * 校验数据源和市场状态
     */
    private void validateDataSourceAndMarket(DataSource dataSource, Market market) {
        if (!dataSource.getEnabled()) {
            throw new BusinessException("数据源未启用");
        }
        if (Boolean.TRUE.equals(dataSource.getDeleted())) {
            throw new BusinessException("数据源已删除");
        }
        if (!market.getEnabled()) {
            throw new BusinessException("市场未启用");
        }
        if (!"BINANCE".equalsIgnoreCase(dataSource.getExchangeType())) {
            throw new BusinessException("仅支持币安数据源");
        }
    }


    /**
     * 拉取并保存 K 线数据
     */
    private int fetchAndSaveKlines(BinanceClient client, String symbolCode, Long symbolId,
                                    String interval, Instant startTime, Instant endTime) {
        int totalSynced = 0;
        Instant currentStart = startTime;
        
        while (currentStart.isBefore(endTime) || currentStart.equals(endTime)) {
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
            totalSynced += saved;
            
            // 如果返回数量小于请求数量，说明已经没有更多数据
            if (klines.size() < MAX_KLINES_PER_REQUEST) {
                break;
            }
            
            // 更新下一次请求的开始时间（最后一根 K 线的开盘时间 + 1ms）
            BinanceKline lastKline = klines.get(klines.size() - 1);
            currentStart = Instant.ofEpochMilli(lastKline.getOpenTime() + 1);
            
            // 防止限流
            sleepBetweenRequests(REQUEST_INTERVAL_MS);
        }
        
        return totalSynced;
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
     * 回补成功后更新 sync_status
     */
    private void updateSyncStatusAfterFill(Long symbolId, String interval, int syncedCount) {
        if (syncedCount <= 0) {
            return;
        }
        
        // 获取最新的 K 线时间
        Instant lastKlineTime = klineService.getMaxOpenTime(symbolId, interval);
        
        // 更新同步状态
        syncService.updateSyncStatus(symbolId, interval, lastKlineTime, syncedCount);
    }

    /**
     * 处理回补失败
     * 
     * 根据重试次数决定状态：
     * - 未达到最大重试次数：状态改为 PENDING，增加重试次数
     * - 达到最大重试次数：状态改为 FAILED
     */
    private void handleFillFailure(DataGap gap, String errorMessage) {
        int maxRetry = systemConfigService.getGapFillMaxRetry();
        int currentRetry = gap.getRetryCount() != null ? gap.getRetryCount() : 0;
        
        // 增加重试次数
        dataGapService.incrementRetryCount(gap.getId());
        
        if (currentRetry + 1 >= maxRetry) {
            // 达到最大重试次数，标记为失败
            dataGapService.updateStatusAndError(gap.getId(), DataGapService.STATUS_FAILED, errorMessage);
            log.warn("Gap fill reached max retries: gapId={}, retryCount={}", gap.getId(), currentRetry + 1);
        } else {
            // 未达到最大重试次数，重置为待回补状态
            dataGapService.updateStatusAndError(gap.getId(), DataGapService.STATUS_PENDING, errorMessage);
            log.info("Gap fill failed, will retry: gapId={}, retryCount={}", gap.getId(), currentRetry + 1);
        }
    }

    /**
     * 重置失败的缺口状态为待回补
     * 
     * @param gapId 缺口ID
     * @return 重置后的缺口
     */
    @Transactional
    public DataGapDTO resetFailedGap(Long gapId) {
        DataGap gap = dataGapService.findById(gapId);
        
        if (!DataGapService.STATUS_FAILED.equals(gap.getStatus())) {
            throw new BusinessException("只能重置失败状态的缺口");
        }
        
        // 重置状态和重试次数
        dataGapMapper.updateStatus(gapId, DataGapService.STATUS_PENDING);
        
        log.info("Reset failed gap: gapId={}", gapId);
        return dataGapService.getById(gapId);
    }

    /**
     * 请求间隔休眠
     */
    private void sleepBetweenRequests(long intervalMs) {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("回补被中断");
        }
    }


    // ==================== 结果类 ====================

    /**
     * 单个缺口回补结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    public static class GapFillResult {
        private Long gapId;
        private boolean success;
        private int syncedCount;
        private String message;

        public static GapFillResult success(Long gapId, int syncedCount, String message) {
            return GapFillResult.builder()
                    .gapId(gapId)
                    .success(true)
                    .syncedCount(syncedCount)
                    .message(message)
                    .build();
        }

        public static GapFillResult failure(Long gapId, String message) {
            return GapFillResult.builder()
                    .gapId(gapId)
                    .success(false)
                    .syncedCount(0)
                    .message(message)
                    .build();
        }
    }

    /**
     * 批量缺口回补结果
     */
    @lombok.Data
    public static class BatchGapFillResult {
        private int totalCount = 0;
        private int successCount = 0;
        private int failureCount = 0;
        private int skippedCount = 0;
        private int totalSyncedKlines = 0;
        private String message;
        private boolean disabled = false;
        private final List<GapFillResult> successResults = new ArrayList<>();
        private final List<GapFillResult> failureResults = new ArrayList<>();
        private final List<SkippedGap> skippedGaps = new ArrayList<>();

        public void addSuccess(GapFillResult result) {
            totalCount++;
            successCount++;
            totalSyncedKlines += result.getSyncedCount();
            successResults.add(result);
        }

        public void addFailure(GapFillResult result) {
            totalCount++;
            failureCount++;
            failureResults.add(result);
        }

        public void addSkipped(Long gapId, String reason) {
            totalCount++;
            skippedCount++;
            skippedGaps.add(new SkippedGap(gapId, reason));
        }

        public static BatchGapFillResult empty() {
            BatchGapFillResult result = new BatchGapFillResult();
            result.setMessage("没有待回补的缺口");
            return result;
        }

        public static BatchGapFillResult disabled(String message) {
            BatchGapFillResult result = new BatchGapFillResult();
            result.setDisabled(true);
            result.setMessage(message);
            return result;
        }

        @lombok.Data
        @lombok.AllArgsConstructor
        public static class SkippedGap {
            private Long gapId;
            private String reason;
        }
    }
}

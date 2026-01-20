package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.entity.Kline;
import com.chanlun.entity.Symbol;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * K线数据服务
 * 
 * 提供 K 线数据的 CRUD 操作，包括：
 * - 批量插入/更新 K 线数据（upsert）
 * - 查询 K 线数据（支持时间范围、分页）
 * - 删除 K 线数据
 * - 统计 K 线数量
 * 
 * 正确性属性 P3: K线数据唯一性
 * - 同一交易对、同一周期、同一开盘时间的K线数据只能有一条
 * - 重复数据应更新而非插入
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KlineService {

    private final KlineMapper klineMapper;
    private final DataGapMapper dataGapMapper;
    private final SyncStatusMapper syncStatusMapper;
    private final SymbolService symbolService;

    /**
     * 支持的 K 线周期列表（不支持 1s）
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 批量插入默认分批大小
     */
    private static final int DEFAULT_BATCH_SIZE = 500;


    /**
     * 批量插入或更新 K 线数据
     * 
     * 使用 PostgreSQL 的 ON CONFLICT ... DO UPDATE 实现 upsert：
     * - 如果 (symbol_id, interval, open_time) 不存在，则插入
     * - 如果已存在，则更新 OHLCV 等字段
     * 
     * 符合正确性属性 P3: K线数据唯一性
     * 
     * @param klines K 线数据列表
     * @return 实际处理的记录数
     */
    @Transactional
    public int batchUpsert(List<Kline> klines) {
        if (klines == null || klines.isEmpty()) {
            return 0;
        }

        // 校验数据
        validateKlines(klines);

        // 分批处理，避免单次 SQL 过大
        int totalProcessed = 0;
        List<List<Kline>> batches = partition(klines, DEFAULT_BATCH_SIZE);
        
        for (List<Kline> batch : batches) {
            int processed = klineMapper.batchUpsert(batch);
            totalProcessed += processed;
        }

        log.debug("Batch upserted {} klines in {} batches", totalProcessed, batches.size());
        return totalProcessed;
    }

    /**
     * 批量插入或更新 K 线数据（指定分批大小）
     * 
     * @param klines K 线数据列表
     * @param batchSize 每批处理的数量
     * @return 实际处理的记录数
     */
    @Transactional
    public int batchUpsert(List<Kline> klines, int batchSize) {
        if (klines == null || klines.isEmpty()) {
            return 0;
        }

        if (batchSize <= 0) {
            batchSize = DEFAULT_BATCH_SIZE;
        }

        // 校验数据
        validateKlines(klines);

        // 分批处理
        int totalProcessed = 0;
        List<List<Kline>> batches = partition(klines, batchSize);
        
        for (List<Kline> batch : batches) {
            int processed = klineMapper.batchUpsert(batch);
            totalProcessed += processed;
        }

        log.debug("Batch upserted {} klines in {} batches (batchSize={})", 
                totalProcessed, batches.size(), batchSize);
        return totalProcessed;
    }

    /**
     * 插入或更新单条 K 线数据
     * 
     * @param kline K 线数据
     * @return 是否成功
     */
    @Transactional
    public boolean upsert(Kline kline) {
        if (kline == null) {
            return false;
        }

        validateKline(kline);
        
        int result = klineMapper.batchUpsert(Collections.singletonList(kline));
        return result > 0;
    }

    /**
     * 根据交易对ID和周期查询 K 线列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return K 线列表（按开盘时间升序）
     */
    public List<Kline> getBySymbolIdAndInterval(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.selectBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 根据交易对ID、周期和时间范围查询 K 线列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（包含）
     * @return K 线列表（按开盘时间升序）
     */
    public List<Kline> getBySymbolIdAndIntervalAndTimeRange(Long symbolId, String interval,
                                                            Instant startTime, Instant endTime) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        validateTimeRange(startTime, endTime);
        
        return klineMapper.selectBySymbolIdAndIntervalAndTimeRange(symbolId, interval, startTime, endTime);
    }


    /**
     * 根据交易对ID、周期和时间范围查询 K 线列表（带数量限制）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（包含）
     * @param limit 返回数量限制（最大 1000）
     * @return K 线列表（按开盘时间升序）
     */
    public List<Kline> getBySymbolIdAndIntervalAndTimeRangeWithLimit(Long symbolId, String interval,
                                                                      Instant startTime, Instant endTime, int limit) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        validateTimeRange(startTime, endTime);
        
        // 限制最大返回数量为 1000
        if (limit <= 0 || limit > 1000) {
            limit = 1000;
        }
        
        return klineMapper.selectBySymbolIdAndIntervalAndTimeRangeWithLimit(
                symbolId, interval, startTime, endTime, limit);
    }

    /**
     * 查询指定交易对和周期的最新一根 K 线
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最新 K 线，不存在返回 null
     */
    public Kline getLatest(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.selectLatestBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 查询指定交易对和周期的最早一根 K 线
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最早 K 线，不存在返回 null
     */
    public Kline getEarliest(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.selectEarliestBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 根据交易对ID、周期和开盘时间查询 K 线
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param openTime 开盘时间
     * @return K 线，不存在返回 null
     */
    public Kline getByOpenTime(Long symbolId, String interval, Instant openTime) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        if (openTime == null) {
            throw new BusinessException("开盘时间不能为空");
        }
        
        return klineMapper.selectBySymbolIdAndIntervalAndOpenTime(symbolId, interval, openTime);
    }

    /**
     * 统计指定交易对和周期的 K 线数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return K 线数量
     */
    public long count(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.countBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 统计指定交易对和周期在时间范围内的 K 线数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（包含）
     * @return K 线数量
     */
    public long countInTimeRange(Long symbolId, String interval, Instant startTime, Instant endTime) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        validateTimeRange(startTime, endTime);
        
        return klineMapper.countBySymbolIdAndIntervalAndTimeRange(symbolId, interval, startTime, endTime);
    }

    /**
     * 获取指定交易对和周期的最大开盘时间
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最大开盘时间，无数据返回 null
     */
    public Instant getMaxOpenTime(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 获取指定交易对和周期的最小开盘时间
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 最小开盘时间，无数据返回 null
     */
    public Instant getMinOpenTime(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        return klineMapper.selectMinOpenTimeBySymbolIdAndInterval(symbolId, interval);
    }


    /**
     * 删除指定交易对和周期在时间范围内的 K 线数据
     * 
     * 同时删除与该时间范围重叠的缺口记录，并重新计算 sync_status.last_kline_time 和 total_klines，
     * 并自动关闭该周期的 auto_gap_fill_enabled（需用户手动开启）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 开始时间（包含）
     * @param endTime 结束时间（包含）
     * @return 删除的记录数
     */
    @Transactional
    public int deleteByTimeRange(Long symbolId, String interval, Instant startTime, Instant endTime) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        validateTimeRange(startTime, endTime);
        
        // 删除 K 线数据
        int deleted = klineMapper.deleteBySymbolIdAndIntervalAndTimeRange(symbolId, interval, startTime, endTime);
        
        // 同步删除与该时间范围重叠的缺口记录
        int gapsDeleted = dataGapMapper.deleteBySymbolIdAndIntervalAndTimeRange(symbolId, interval, startTime, endTime);
        
        // 重新计算 sync_status.last_kline_time 和 total_klines，并关闭自动回补
        if (deleted > 0) {
            recalculateLastKlineTime(symbolId, interval);
            recalculateTotalKlines(symbolId, interval);
            disableAutoGapFill(symbolId, interval);
        }
        
        if (deleted > 0 || gapsDeleted > 0) {
            log.info("Deleted {} klines and {} gaps: symbolId={}, interval={}, timeRange=[{}, {}]",
                    deleted, gapsDeleted, symbolId, interval, startTime, endTime);
        }
        
        return deleted;
    }

    /**
     * 删除指定交易对的所有 K 线数据
     * 
     * 同时删除该交易对的所有缺口记录，并重新计算所有周期的 sync_status.last_kline_time 和 total_klines，
     * 并自动关闭所有周期的 auto_gap_fill_enabled（需用户手动开启）
     * 
     * @param symbolId 交易对ID
     * @return 删除的记录数
     */
    @Transactional
    public int deleteBySymbolId(Long symbolId) {
        validateSymbolId(symbolId);
        
        // 删除 K 线数据
        int deleted = klineMapper.deleteBySymbolId(symbolId);
        
        // 同步删除该交易对的所有缺口记录
        int gapsDeleted = dataGapMapper.deleteBySymbolId(symbolId);
        
        // 重新计算所有周期的 sync_status.last_kline_time 和 total_klines（设为 NULL/0），并关闭自动回补
        if (deleted > 0) {
            for (String interval : VALID_INTERVALS) {
                recalculateLastKlineTime(symbolId, interval);
                recalculateTotalKlines(symbolId, interval);
                disableAutoGapFill(symbolId, interval);
            }
        }
        
        if (deleted > 0 || gapsDeleted > 0) {
            log.info("Deleted {} klines and {} gaps for symbolId={}", deleted, gapsDeleted, symbolId);
        }
        
        return deleted;
    }

    /**
     * 删除指定交易对和周期的所有 K 线数据
     * 
     * 同时删除该交易对该周期的所有缺口记录，并重新计算 sync_status.last_kline_time 和 total_klines，
     * 并自动关闭该周期的 auto_gap_fill_enabled（需用户手动开启）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 删除的记录数
     */
    @Transactional
    public int deleteBySymbolIdAndInterval(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        // 删除 K 线数据
        int deleted = klineMapper.deleteBySymbolIdAndInterval(symbolId, interval);
        
        // 同步删除该交易对该周期的所有缺口记录
        int gapsDeleted = dataGapMapper.deleteBySymbolIdAndInterval(symbolId, interval);
        
        // 重新计算 sync_status.last_kline_time 和 total_klines（设为 NULL/0），并关闭自动回补
        if (deleted > 0) {
            recalculateLastKlineTime(symbolId, interval);
            recalculateTotalKlines(symbolId, interval);
            disableAutoGapFill(symbolId, interval);
        }
        
        if (deleted > 0 || gapsDeleted > 0) {
            log.info("Deleted {} klines and {} gaps: symbolId={}, interval={}", deleted, gapsDeleted, symbolId, interval);
        }
        
        return deleted;
    }

    /**
     * 检查 K 线是否存在
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param openTime 开盘时间
     * @return 是否存在
     */
    public boolean exists(Long symbolId, String interval, Instant openTime) {
        return getByOpenTime(symbolId, interval, openTime) != null;
    }

    /**
     * 获取支持的 K 线周期列表
     * 
     * @return 周期列表
     */
    public Set<String> getValidIntervals() {
        return VALID_INTERVALS;
    }

    // ==================== 私有方法 ====================

    /**
     * 校验 K 线数据列表
     */
    private void validateKlines(List<Kline> klines) {
        for (Kline kline : klines) {
            validateKline(kline);
        }
    }

    /**
     * 校验单条 K 线数据
     */
    private void validateKline(Kline kline) {
        if (kline.getSymbolId() == null) {
            throw new BusinessException("交易对ID不能为空");
        }
        if (kline.getInterval() == null || kline.getInterval().isEmpty()) {
            throw new BusinessException("时间周期不能为空");
        }
        if (!VALID_INTERVALS.contains(kline.getInterval())) {
            throw new BusinessException("不支持的时间周期: " + kline.getInterval());
        }
        if (kline.getOpenTime() == null) {
            throw new BusinessException("开盘时间不能为空");
        }
        if (kline.getOpen() == null) {
            throw new BusinessException("开盘价不能为空");
        }
        if (kline.getHigh() == null) {
            throw new BusinessException("最高价不能为空");
        }
        if (kline.getLow() == null) {
            throw new BusinessException("最低价不能为空");
        }
        if (kline.getClose() == null) {
            throw new BusinessException("收盘价不能为空");
        }
        if (kline.getCloseTime() == null) {
            throw new BusinessException("收盘时间不能为空");
        }
    }

    /**
     * 校验交易对ID
     */
    private void validateSymbolId(Long symbolId) {
        if (symbolId == null) {
            throw new BusinessException("交易对ID不能为空");
        }
    }

    /**
     * 校验时间周期
     */
    private void validateInterval(String interval) {
        if (interval == null || interval.isEmpty()) {
            throw new BusinessException("时间周期不能为空");
        }
        if (!VALID_INTERVALS.contains(interval)) {
            throw new BusinessException("不支持的时间周期: " + interval + 
                    "。支持的周期: " + String.join(", ", VALID_INTERVALS));
        }
    }

    /**
     * 校验时间范围
     */
    private void validateTimeRange(Instant startTime, Instant endTime) {
        if (startTime == null) {
            throw new BusinessException("开始时间不能为空");
        }
        if (endTime == null) {
            throw new BusinessException("结束时间不能为空");
        }
        if (startTime.isAfter(endTime)) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
    }

    /**
     * 将列表分割成指定大小的批次
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * 重新计算 sync_status.last_kline_time
     * 
     * 删除历史数据后，需要重新计算该交易对该周期的最后K线时间
     * 如果没有K线数据，则设为 NULL
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     */
    private void recalculateLastKlineTime(Long symbolId, String interval) {
        // 查询当前最大的 open_time
        Instant maxOpenTime = klineMapper.selectMaxOpenTimeBySymbolIdAndInterval(symbolId, interval);
        
        // 更新 sync_status.last_kline_time
        int updated = syncStatusMapper.updateLastKlineTime(symbolId, interval, maxOpenTime);
        
        if (updated > 0) {
            log.debug("Recalculated last_kline_time: symbolId={}, interval={}, lastKlineTime={}",
                    symbolId, interval, maxOpenTime);
        }
    }

    /**
     * 重新计算 sync_status.total_klines
     * 
     * 删除历史数据后，需要重新计算该交易对该周期的总K线数量
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     */
    private void recalculateTotalKlines(Long symbolId, String interval) {
        // 统计当前 K 线数量
        long totalKlines = klineMapper.countBySymbolIdAndInterval(symbolId, interval);
        
        // 更新 sync_status.total_klines
        int updated = syncStatusMapper.updateTotalKlines(symbolId, interval, totalKlines);
        
        if (updated > 0) {
            log.debug("Recalculated total_klines: symbolId={}, interval={}, totalKlines={}",
                    symbolId, interval, totalKlines);
        }
    }

    /**
     * 关闭指定交易对和周期的自动缺口回补开关
     * 
     * 删除历史数据后，需要自动关闭该周期的 auto_gap_fill_enabled，
     * 避免自动回补刚删除的数据，需用户手动开启
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     */
    private void disableAutoGapFill(Long symbolId, String interval) {
        int updated = syncStatusMapper.updateAutoGapFillEnabled(symbolId, interval, false);
        
        if (updated > 0) {
            log.info("Disabled auto_gap_fill_enabled after deletion: symbolId={}, interval={}",
                    symbolId, interval);
        }
    }
}

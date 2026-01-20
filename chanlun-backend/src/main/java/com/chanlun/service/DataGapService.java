package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.dto.DataGapDTO;
import com.chanlun.dto.GapDetectResult;
import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.DataGap;
import com.chanlun.entity.DataSource;
import com.chanlun.entity.Kline;
import com.chanlun.entity.Market;
import com.chanlun.entity.Symbol;
import com.chanlun.entity.SyncStatus;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.DataGapMapper;
import com.chanlun.mapper.KlineMapper;
import com.chanlun.mapper.SyncStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 数据缺口服务
 * 
 * 提供数据缺口的检测、查询和管理功能：
 * - 基于时间连续性检测 K 线数据缺口
 * - 单交易对缺口检测
 * - 批量缺口检测
 * - 缺口状态管理
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataGapService {

    private final DataGapMapper dataGapMapper;
    private final KlineMapper klineMapper;
    private final SyncStatusMapper syncStatusMapper;
    private final SymbolService symbolService;
    private final MarketService marketService;
    private final DataSourceService dataSourceService;
    private final SyncFilterService syncFilterService;

    /**
     * 缺口状态常量
     */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FILLING = "FILLING";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 支持的 K 线周期
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 获取缺口列表（分页）
     */
    public IPage<DataGapDTO> list(int page, int size, Long symbolId, String interval, String status) {
        Page<DataGap> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<DataGap> wrapper = new LambdaQueryWrapper<>();
        if (symbolId != null) {
            wrapper.eq(DataGap::getSymbolId, symbolId);
        }
        if (StringUtils.hasText(interval)) {
            wrapper.eq(DataGap::getInterval, interval);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(DataGap::getStatus, status);
        }
        wrapper.orderByDesc(DataGap::getCreatedAt);
        
        IPage<DataGap> result = dataGapMapper.selectPage(pageParam, wrapper);
        return result.convert(this::toDTO);
    }

    /**
     * 根据 ID 获取缺口
     */
    public DataGapDTO getById(Long id) {
        DataGap gap = findById(id);
        return toDTO(gap);
    }

    /**
     * 根据 ID 查找缺口实体
     */
    public DataGap findById(Long id) {
        DataGap gap = dataGapMapper.selectById(id);
        if (gap == null) {
            throw new ResourceNotFoundException("缺口不存在: " + id);
        }
        return gap;
    }


    /**
     * 检测单个交易对单个周期的数据缺口
     * 
     * 基于时间连续性检测算法：
     * 1. 获取该交易对该周期的所有 K 线数据（按时间排序）
     * 2. 遍历相邻 K 线，检查时间间隔是否符合周期
     * 3. 如果间隔大于预期，则记录为缺口
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 检测结果
     */
    @Transactional
    public GapDetectResult detectGaps(Long symbolId, String interval) {
        // 校验参数
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        // 获取交易对信息
        Symbol symbol = symbolService.findById(symbolId);
        
        // 检查是否符合检测条件
        if (!isEligibleForGapDetect(symbol, interval)) {
            return GapDetectResult.failure("交易对不符合缺口检测条件");
        }
        
        // 执行检测
        List<DataGap> newGaps = detectGapsForSymbolInterval(symbolId, interval);
        
        // 保存新检测到的缺口
        int newGapCount = 0;
        if (!newGaps.isEmpty()) {
            newGapCount = saveNewGaps(newGaps);
        }
        
        // 查询该交易对该周期的所有缺口
        List<DataGap> allGaps = dataGapMapper.selectBySymbolIdAndInterval(symbolId, interval);
        List<DataGapDTO> gapDTOs = allGaps.stream().map(this::toDTO).toList();
        
        log.info("Gap detection completed: symbolId={}, interval={}, newGaps={}, totalGaps={}",
                symbolId, interval, newGapCount, allGaps.size());
        
        return GapDetectResult.success(
                "缺口检测完成",
                1,
                1,
                newGapCount,
                allGaps.size(),
                gapDTOs
        );
    }

    /**
     * 批量检测所有符合条件的交易对的数据缺口
     * 
     * 筛选条件：
     * - 数据源已启用且未删除
     * - 市场已启用
     * - 交易对 history_sync_enabled = true
     * - 交易对配置了 sync_intervals
     * 
     * @return 检测结果
     */
    @Transactional
    public GapDetectResult detectAllGaps() {
        // 获取符合条件的交易对
        List<SymbolDTO> targets = syncFilterService.getGapDetectTargets();
        
        if (targets.isEmpty()) {
            return GapDetectResult.success("没有符合条件的交易对", 0, 0, 0, 0, Collections.emptyList());
        }
        
        int totalSymbols = 0;
        int totalIntervals = 0;
        int totalNewGaps = 0;
        List<DataGapDTO> allNewGaps = new ArrayList<>();
        
        for (SymbolDTO symbolDTO : targets) {
            List<String> intervals = syncFilterService.getValidSyncIntervals(symbolDTO);
            
            for (String interval : intervals) {
                try {
                    List<DataGap> newGaps = detectGapsForSymbolInterval(symbolDTO.getId(), interval);
                    
                    if (!newGaps.isEmpty()) {
                        int saved = saveNewGaps(newGaps);
                        totalNewGaps += saved;
                        
                        // 转换为 DTO
                        for (DataGap gap : newGaps) {
                            allNewGaps.add(toDTO(gap, symbolDTO));
                        }
                    }
                    
                    totalIntervals++;
                } catch (Exception e) {
                    log.warn("Gap detection failed for symbol {} interval {}: {}", 
                            symbolDTO.getSymbol(), interval, e.getMessage());
                }
            }
            
            totalSymbols++;
        }
        
        // 统计总缺口数
        long totalGapCount = dataGapMapper.countByStatus(STATUS_PENDING) +
                            dataGapMapper.countByStatus(STATUS_FILLING) +
                            dataGapMapper.countByStatus(STATUS_FAILED);
        
        log.info("Batch gap detection completed: symbols={}, intervals={}, newGaps={}, totalGaps={}",
                totalSymbols, totalIntervals, totalNewGaps, totalGapCount);
        
        return GapDetectResult.success(
                "批量缺口检测完成",
                totalSymbols,
                totalIntervals,
                totalNewGaps,
                (int) totalGapCount,
                allNewGaps
        );
    }

    /**
     * 检测单个交易对单个周期的缺口（内部方法）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 检测到的新缺口列表
     */
    private List<DataGap> detectGapsForSymbolInterval(Long symbolId, String interval) {
        // 获取该周期的时间间隔（毫秒）
        long intervalMs = getIntervalMillis(interval);
        
        // 获取所有 K 线数据（按时间排序）
        List<Kline> klines = klineMapper.selectBySymbolIdAndInterval(symbolId, interval);
        
        if (klines.size() < 2) {
            // 数据不足，无法检测缺口
            return Collections.emptyList();
        }
        
        List<DataGap> gaps = new ArrayList<>();
        
        // 遍历相邻 K 线，检测缺口
        for (int i = 0; i < klines.size() - 1; i++) {
            Kline current = klines.get(i);
            Kline next = klines.get(i + 1);
            
            // 计算实际时间间隔
            long actualGap = Duration.between(current.getOpenTime(), next.getOpenTime()).toMillis();
            
            // 允许一定的误差（1秒）
            long tolerance = 1000;
            
            // 如果实际间隔大于预期间隔（加上容差），则存在缺口
            if (actualGap > intervalMs + tolerance) {
                // 计算缺失的 K 线数量
                int missingCount = (int) ((actualGap - intervalMs) / intervalMs);
                
                if (missingCount > 0) {
                    // 缺口开始时间 = 当前 K 线的下一个周期开始时间
                    Instant gapStart = current.getOpenTime().plusMillis(intervalMs);
                    // 缺口结束时间 = 下一根 K 线的前一个周期开始时间
                    Instant gapEnd = next.getOpenTime().minusMillis(intervalMs);
                    
                    // 确保 gapEnd >= gapStart
                    if (!gapEnd.isBefore(gapStart)) {
                        // 检查是否已存在重叠的缺口
                        List<DataGap> existing = dataGapMapper.selectOverlapping(
                                symbolId, interval, gapStart, gapEnd);
                        
                        if (existing.isEmpty()) {
                            DataGap gap = DataGap.builder()
                                    .symbolId(symbolId)
                                    .interval(interval)
                                    .gapStart(gapStart)
                                    .gapEnd(gapEnd)
                                    .missingCount(missingCount)
                                    .status(STATUS_PENDING)
                                    .retryCount(0)
                                    .build();
                            gaps.add(gap);
                        }
                    }
                }
            }
        }
        
        return gaps;
    }

    /**
     * 保存新检测到的缺口
     * 
     * @param gaps 缺口列表
     * @return 保存的数量
     */
    private int saveNewGaps(List<DataGap> gaps) {
        if (gaps.isEmpty()) {
            return 0;
        }
        
        // 批量插入
        return dataGapMapper.batchInsert(gaps);
    }

    /**
     * 获取周期对应的毫秒数
     * 
     * @param interval 时间周期
     * @return 毫秒数
     */
    public static long getIntervalMillis(String interval) {
        return switch (interval) {
            case "1m" -> 60 * 1000L;
            case "3m" -> 3 * 60 * 1000L;
            case "5m" -> 5 * 60 * 1000L;
            case "15m" -> 15 * 60 * 1000L;
            case "30m" -> 30 * 60 * 1000L;
            case "1h" -> 60 * 60 * 1000L;
            case "2h" -> 2 * 60 * 60 * 1000L;
            case "4h" -> 4 * 60 * 60 * 1000L;
            case "6h" -> 6 * 60 * 60 * 1000L;
            case "8h" -> 8 * 60 * 60 * 1000L;
            case "12h" -> 12 * 60 * 60 * 1000L;
            case "1d" -> 24 * 60 * 60 * 1000L;
            case "3d" -> 3 * 24 * 60 * 60 * 1000L;
            case "1w" -> 7 * 24 * 60 * 60 * 1000L;
            case "1M" -> 30 * 24 * 60 * 60 * 1000L; // 近似值
            default -> throw new BusinessException("不支持的时间周期: " + interval);
        };
    }

    /**
     * 检查交易对是否符合缺口检测条件
     * 
     * @param symbol 交易对
     * @param interval 时间周期
     * @return 是否符合条件
     */
    private boolean isEligibleForGapDetect(Symbol symbol, String interval) {
        // 检查历史同步是否启用
        if (!Boolean.TRUE.equals(symbol.getHistorySyncEnabled())) {
            return false;
        }
        
        // 检查是否配置了该周期
        if (!StringUtils.hasText(symbol.getSyncIntervals())) {
            return false;
        }
        
        List<String> intervals = Arrays.asList(symbol.getSyncIntervals().split(","));
        return intervals.stream().map(String::trim).anyMatch(i -> i.equals(interval));
    }


    /**
     * 更新缺口状态
     * 
     * @param id 缺口ID
     * @param status 新状态
     * @return 更新后的缺口
     */
    @Transactional
    public DataGapDTO updateStatus(Long id, String status) {
        validateStatus(status);
        
        DataGap gap = findById(id);
        
        // 验证状态流转
        validateStatusTransition(gap.getStatus(), status);
        
        dataGapMapper.updateStatus(id, status);
        gap.setStatus(status);
        
        log.info("Updated gap status: id={}, status={}", id, status);
        return toDTO(gap);
    }

    /**
     * 更新缺口状态和错误信息
     * 
     * @param id 缺口ID
     * @param status 新状态
     * @param errorMessage 错误信息
     * @return 更新后的缺口
     */
    @Transactional
    public DataGapDTO updateStatusAndError(Long id, String status, String errorMessage) {
        validateStatus(status);
        
        DataGap gap = findById(id);
        
        dataGapMapper.updateStatusAndError(id, status, errorMessage);
        gap.setStatus(status);
        gap.setErrorMessage(errorMessage);
        
        log.info("Updated gap status and error: id={}, status={}, error={}", id, status, errorMessage);
        return toDTO(gap);
    }

    /**
     * 增加重试次数
     * 
     * @param id 缺口ID
     */
    @Transactional
    public void incrementRetryCount(Long id) {
        dataGapMapper.incrementRetryCount(id);
    }

    /**
     * 获取待回补的缺口列表
     * 
     * @param limit 返回数量限制
     * @return 缺口列表
     */
    public List<DataGapDTO> getPendingGaps(int limit) {
        List<DataGap> gaps = dataGapMapper.selectPendingWithLimit(limit);
        return gaps.stream().map(this::toDTO).toList();
    }

    /**
     * 根据交易对ID和周期获取缺口列表
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 缺口列表
     */
    public List<DataGapDTO> getBySymbolIdAndInterval(Long symbolId, String interval) {
        List<DataGap> gaps = dataGapMapper.selectBySymbolIdAndInterval(symbolId, interval);
        return gaps.stream().map(this::toDTO).toList();
    }

    /**
     * 根据交易对ID获取缺口列表
     * 
     * @param symbolId 交易对ID
     * @return 缺口列表
     */
    public List<DataGapDTO> getBySymbolId(Long symbolId) {
        List<DataGap> gaps = dataGapMapper.selectBySymbolId(symbolId);
        return gaps.stream().map(this::toDTO).toList();
    }

    /**
     * 统计待回补的缺口数量
     * 
     * @return 缺口数量
     */
    public long countPending() {
        return dataGapMapper.countByStatus(STATUS_PENDING);
    }

    /**
     * 统计指定状态的缺口数量
     * 
     * @param status 状态
     * @return 缺口数量
     */
    public long countByStatus(String status) {
        return dataGapMapper.countByStatus(status);
    }

    // ==================== 私有方法 ====================

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
        if (!StringUtils.hasText(interval)) {
            throw new BusinessException("时间周期不能为空");
        }
        if (!VALID_INTERVALS.contains(interval)) {
            throw new BusinessException("不支持的时间周期: " + interval);
        }
    }

    /**
     * 校验状态
     */
    private void validateStatus(String status) {
        if (!StringUtils.hasText(status)) {
            throw new BusinessException("状态不能为空");
        }
        if (!STATUS_PENDING.equals(status) && !STATUS_FILLING.equals(status) &&
            !STATUS_FILLED.equals(status) && !STATUS_FAILED.equals(status)) {
            throw new BusinessException("无效的状态: " + status);
        }
    }

    /**
     * 验证状态流转
     * 
     * 正确性属性 P4: 数据缺口状态流转
     * - 缺口状态只能按 PENDING → FILLING → FILLED/FAILED 流转
     * - FILLED 状态的缺口不能再次回补
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // FILLED 状态不能再变更
        if (STATUS_FILLED.equals(currentStatus)) {
            throw new BusinessException("已回补的缺口不能再次变更状态");
        }
        
        // PENDING 只能变为 FILLING
        if (STATUS_PENDING.equals(currentStatus) && !STATUS_FILLING.equals(newStatus)) {
            if (!STATUS_PENDING.equals(newStatus)) { // 允许保持 PENDING
                throw new BusinessException("待回补状态只能变更为回补中");
            }
        }
        
        // FILLING 只能变为 FILLED 或 FAILED 或 PENDING（重试）
        if (STATUS_FILLING.equals(currentStatus)) {
            if (!STATUS_FILLED.equals(newStatus) && !STATUS_FAILED.equals(newStatus) && 
                !STATUS_PENDING.equals(newStatus)) {
                throw new BusinessException("回补中状态只能变更为已回补、回补失败或待回补");
            }
        }
        
        // FAILED 可以变为 PENDING（手动重置）
        if (STATUS_FAILED.equals(currentStatus) && !STATUS_PENDING.equals(newStatus)) {
            throw new BusinessException("回补失败状态只能重置为待回补");
        }
    }

    /**
     * 转换为 DTO
     */
    private DataGapDTO toDTO(DataGap entity) {
        Symbol symbol = null;
        Market market = null;
        DataSource dataSource = null;
        
        try {
            symbol = symbolService.findById(entity.getSymbolId());
            market = marketService.findById(symbol.getMarketId());
            dataSource = dataSourceService.findById(market.getDataSourceId());
        } catch (ResourceNotFoundException e) {
            // 忽略
        }
        
        return DataGapDTO.builder()
                .id(entity.getId())
                .symbolId(entity.getSymbolId())
                .symbol(symbol != null ? symbol.getSymbol() : null)
                .marketId(market != null ? market.getId() : null)
                .marketName(market != null ? market.getName() : null)
                .dataSourceId(dataSource != null ? dataSource.getId() : null)
                .dataSourceName(dataSource != null ? dataSource.getName() : null)
                .interval(entity.getInterval())
                .gapStart(entity.getGapStart())
                .gapEnd(entity.getGapEnd())
                .missingCount(entity.getMissingCount())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 转换为 DTO（带交易对信息）
     */
    private DataGapDTO toDTO(DataGap entity, SymbolDTO symbolDTO) {
        return DataGapDTO.builder()
                .id(entity.getId())
                .symbolId(entity.getSymbolId())
                .symbol(symbolDTO.getSymbol())
                .marketId(symbolDTO.getMarketId())
                .marketName(symbolDTO.getMarketName())
                .dataSourceId(symbolDTO.getDataSourceId())
                .dataSourceName(symbolDTO.getDataSourceName())
                .interval(entity.getInterval())
                .gapStart(entity.getGapStart())
                .gapEnd(entity.getGapEnd())
                .missingCount(entity.getMissingCount())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

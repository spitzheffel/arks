package com.chanlun.service;

import com.chanlun.dto.SymbolDTO;
import com.chanlun.entity.Symbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 同步对象筛选服务
 * 
 * 提供定时任务所需的同步对象筛选逻辑：
 * - 筛选启用实时同步的交易对（检查数据源、市场、交易对状态）
 * - 筛选启用历史同步的交易对（检查数据源、市场、交易对状态）
 * - 筛选需要缺口检测的交易对
 * - 筛选需要缺口回补的交易对
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncFilterService {

    private final SymbolService symbolService;
    private final SystemConfigService systemConfigService;

    /**
     * 支持的 K 线周期
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 获取需要实时同步的交易对列表
     * 
     * 筛选条件：
     * - sync.realtime.enabled = true（全局开关）
     * - 数据源已启用且未删除
     * - 市场已启用
     * - 交易对 realtime_sync_enabled = true
     * - 交易对配置了 sync_intervals
     * 
     * @return 符合条件的交易对列表
     */
    public List<Symbol> getRealtimeSyncTargets() {
        // 检查全局开关
        if (!systemConfigService.isRealtimeSyncEnabled()) {
            log.debug("Realtime sync is disabled globally");
            return Collections.emptyList();
        }

        // 获取启用实时同步的交易对（已包含数据源和市场状态检查）
        List<Symbol> symbols = symbolService.getRealtimeSyncEnabledSymbols();

        // 过滤掉没有配置同步周期的交易对
        List<Symbol> filtered = symbols.stream()
                .filter(s -> hasValidSyncIntervals(s.getSyncIntervals()))
                .toList();

        log.debug("Found {} realtime sync targets (filtered from {})", 
                filtered.size(), symbols.size());
        return filtered;
    }

    /**
     * 获取需要历史同步的交易对列表
     * 
     * 筛选条件：
     * - sync.history.auto = true（自动同步开关）
     * - 数据源已启用且未删除
     * - 市场已启用
     * - 交易对 history_sync_enabled = true
     * - 交易对配置了 sync_intervals
     * 
     * @return 符合条件的交易对 DTO 列表
     */
    public List<SymbolDTO> getHistorySyncTargets() {
        // 检查自动同步开关
        if (!systemConfigService.isHistoryAutoSyncEnabled()) {
            log.debug("History auto sync is disabled");
            return Collections.emptyList();
        }

        // 获取启用历史同步的交易对（已包含数据源和市场状态检查）
        List<SymbolDTO> symbols = symbolService.getHistorySyncEnabled();

        // 过滤掉没有配置同步周期的交易对
        List<SymbolDTO> filtered = symbols.stream()
                .filter(s -> s.getSyncIntervals() != null && !s.getSyncIntervals().isEmpty())
                .toList();

        log.debug("Found {} history sync targets (filtered from {})", 
                filtered.size(), symbols.size());
        return filtered;
    }

    /**
     * 获取需要缺口检测的交易对列表
     * 
     * 筛选条件：
     * - 数据源已启用且未删除
     * - 市场已启用
     * - 交易对 history_sync_enabled = true
     * - 交易对配置了 sync_intervals
     * 
     * @return 符合条件的交易对 DTO 列表
     */
    public List<SymbolDTO> getGapDetectTargets() {
        // 获取启用历史同步的交易对
        List<SymbolDTO> symbols = symbolService.getHistorySyncEnabled();

        // 过滤掉没有配置同步周期的交易对
        List<SymbolDTO> filtered = symbols.stream()
                .filter(s -> s.getSyncIntervals() != null && !s.getSyncIntervals().isEmpty())
                .toList();

        log.debug("Found {} gap detect targets (filtered from {})", 
                filtered.size(), symbols.size());
        return filtered;
    }

    /**
     * 获取需要自动缺口回补的交易对列表
     * 
     * 筛选条件：
     * - sync.gap_fill.auto = true（全局开关）
     * - 数据源已启用且未删除
     * - 市场已启用
     * - 交易对 history_sync_enabled = true
     * - 交易对配置了 sync_intervals
     * 
     * @return 符合条件的交易对 DTO 列表
     */
    public List<SymbolDTO> getAutoGapFillTargets() {
        // 检查全局自动回补开关
        if (!systemConfigService.isAutoGapFillEnabled()) {
            log.debug("Auto gap fill is disabled globally");
            return Collections.emptyList();
        }

        // 获取缺口检测目标（同样的筛选条件）
        return getGapDetectTargets();
    }

    /**
     * 获取交易对的有效同步周期列表
     * 
     * @param symbol 交易对
     * @return 有效的同步周期列表
     */
    public List<String> getValidSyncIntervals(Symbol symbol) {
        return parseAndValidateIntervals(symbol.getSyncIntervals());
    }

    /**
     * 获取交易对的有效同步周期列表
     * 
     * @param symbolDTO 交易对 DTO
     * @return 有效的同步周期列表
     */
    public List<String> getValidSyncIntervals(SymbolDTO symbolDTO) {
        if (symbolDTO.getSyncIntervals() == null) {
            return Collections.emptyList();
        }
        return symbolDTO.getSyncIntervals().stream()
                .filter(VALID_INTERVALS::contains)
                .toList();
    }

    /**
     * 检查是否有有效的同步周期配置
     * 
     * @param syncIntervals 同步周期字符串
     * @return 是否有有效配置
     */
    private boolean hasValidSyncIntervals(String syncIntervals) {
        if (syncIntervals == null || syncIntervals.isBlank()) {
            return false;
        }
        return Arrays.stream(syncIntervals.split(","))
                .map(String::trim)
                .anyMatch(VALID_INTERVALS::contains);
    }

    /**
     * 解析并验证同步周期
     * 
     * @param syncIntervals 同步周期字符串
     * @return 有效的同步周期列表
     */
    private List<String> parseAndValidateIntervals(String syncIntervals) {
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
     * 检查交易对是否符合实时同步条件
     * 
     * @param symbol 交易对
     * @return 是否符合条件
     */
    public boolean isEligibleForRealtimeSync(Symbol symbol) {
        if (!systemConfigService.isRealtimeSyncEnabled()) {
            return false;
        }
        if (!Boolean.TRUE.equals(symbol.getRealtimeSyncEnabled())) {
            return false;
        }
        return hasValidSyncIntervals(symbol.getSyncIntervals());
    }

    /**
     * 检查交易对是否符合历史同步条件
     * 
     * @param symbol 交易对
     * @return 是否符合条件
     */
    public boolean isEligibleForHistorySync(Symbol symbol) {
        if (!Boolean.TRUE.equals(symbol.getHistorySyncEnabled())) {
            return false;
        }
        return hasValidSyncIntervals(symbol.getSyncIntervals());
    }
}

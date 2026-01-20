package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chanlun.dto.ApiResponse;
import com.chanlun.dto.StatusUpdateRequest;
import com.chanlun.dto.SymbolDTO;
import com.chanlun.dto.SymbolIntervalsRequest;
import com.chanlun.service.SymbolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * 交易对管理控制器
 * 
 * 提供交易对的 REST API
 * 
 * API 路径: /api/v1/symbols
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/symbols")
@RequiredArgsConstructor
public class SymbolController {

    private final SymbolService symbolService;

    /**
     * 获取交易对列表（分页）
     * 
     * GET /api/v1/symbols
     * 
     * @param page 页码（从1开始）
     * @param size 每页数量（默认20，最大100）
     * @param marketId 市场ID筛选
     * @param dataSourceId 数据源ID筛选
     * @param keyword 搜索关键词（交易对代码、基础货币、报价货币）
     * @param realtimeSyncEnabled 实时同步状态筛选
     * @param historySyncEnabled 历史同步状态筛选
     */
    @GetMapping
    public ApiResponse<IPage<SymbolDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long marketId,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean realtimeSyncEnabled,
            @RequestParam(required = false) Boolean historySyncEnabled) {
        
        // 限制每页最大数量
        if (size > 100) {
            size = 100;
        }
        
        IPage<SymbolDTO> result = symbolService.list(page, size, marketId, dataSourceId, 
                keyword, realtimeSyncEnabled, historySyncEnabled);
        return ApiResponse.success(result);
    }


    /**
     * 获取所有交易对（不分页）
     * 
     * GET /api/v1/symbols/all
     * 
     * @param marketId 市场ID筛选
     * @param dataSourceId 数据源ID筛选
     * @param keyword 搜索关键词
     * @param realtimeSyncEnabled 实时同步状态筛选
     * @param historySyncEnabled 历史同步状态筛选
     */
    @GetMapping("/all")
    public ApiResponse<List<SymbolDTO>> listAll(
            @RequestParam(required = false) Long marketId,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean realtimeSyncEnabled,
            @RequestParam(required = false) Boolean historySyncEnabled) {
        
        List<SymbolDTO> result = symbolService.listAll(marketId, dataSourceId, keyword,
                realtimeSyncEnabled, historySyncEnabled);
        return ApiResponse.success(result);
    }

    /**
     * 获取交易对详情
     * 
     * GET /api/v1/symbols/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<SymbolDTO> getById(@PathVariable Long id) {
        SymbolDTO symbol = symbolService.getById(id);
        return ApiResponse.success(symbol);
    }

    /**
     * 开启/关闭实时同步
     * 
     * PATCH /api/v1/symbols/{id}/realtime-sync
     * 
     * 开启实时同步前会验证市场和数据源是否启用
     */
    @PatchMapping("/{id}/realtime-sync")
    public ApiResponse<SymbolDTO> updateRealtimeSync(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        
        SymbolDTO symbol = symbolService.updateRealtimeSyncStatus(id, request.getEnabled());
        String message = request.getEnabled() ? "实时同步已开启" : "实时同步已关闭";
        return ApiResponse.success(message, symbol);
    }

    /**
     * 开启/关闭历史同步
     * 
     * PATCH /api/v1/symbols/{id}/history-sync
     * 
     * 开启历史同步前会验证市场和数据源是否启用
     */
    @PatchMapping("/{id}/history-sync")
    public ApiResponse<SymbolDTO> updateHistorySync(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        
        SymbolDTO symbol = symbolService.updateHistorySyncStatus(id, request.getEnabled());
        String message = request.getEnabled() ? "历史同步已开启" : "历史同步已关闭";
        return ApiResponse.success(message, symbol);
    }

    /**
     * 配置同步周期
     * 
     * PUT /api/v1/symbols/{id}/intervals
     * 
     * 支持的周期: 1m, 3m, 5m, 15m, 30m, 1h, 2h, 4h, 6h, 8h, 12h, 1d, 3d, 1w, 1M
     * 不支持 1s
     */
    @PutMapping("/{id}/intervals")
    public ApiResponse<SymbolDTO> updateIntervals(
            @PathVariable Long id,
            @Valid @RequestBody SymbolIntervalsRequest request) {
        
        SymbolDTO symbol = symbolService.updateSyncIntervals(id, request.getIntervals());
        return ApiResponse.success("同步周期配置成功", symbol);
    }

    /**
     * 获取支持的同步周期列表
     * 
     * GET /api/v1/symbols/intervals
     */
    @GetMapping("/intervals")
    public ApiResponse<Set<String>> getValidIntervals() {
        Set<String> intervals = symbolService.getValidIntervals();
        return ApiResponse.success(intervals);
    }
}

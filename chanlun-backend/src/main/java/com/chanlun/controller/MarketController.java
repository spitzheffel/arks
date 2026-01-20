package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chanlun.dto.ApiResponse;
import com.chanlun.dto.MarketDTO;
import com.chanlun.dto.MarketSyncResult;
import com.chanlun.dto.StatusUpdateRequest;
import com.chanlun.dto.SymbolSyncResult;
import com.chanlun.service.MarketService;
import com.chanlun.service.SymbolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 市场管理控制器
 * 
 * 提供市场的 REST API
 * 
 * API 路径: /api/v1/markets
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/markets")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final SymbolService symbolService;

    /**
     * 获取市场列表（分页）
     * 
     * GET /api/v1/markets
     * 
     * @param page 页码（从1开始）
     * @param size 每页数量（默认20，最大100）
     * @param dataSourceId 数据源ID筛选
     * @param marketType 市场类型筛选
     * @param enabled 启用状态筛选
     */
    @GetMapping
    public ApiResponse<IPage<MarketDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String marketType,
            @RequestParam(required = false) Boolean enabled) {
        
        // 限制每页最大数量
        if (size > 100) {
            size = 100;
        }
        
        IPage<MarketDTO> result = marketService.list(page, size, dataSourceId, marketType, enabled);
        return ApiResponse.success(result);
    }

    /**
     * 获取所有市场（不分页）
     * 
     * GET /api/v1/markets/all
     * 
     * @param dataSourceId 数据源ID筛选
     * @param marketType 市场类型筛选
     * @param enabled 启用状态筛选
     */
    @GetMapping("/all")
    public ApiResponse<List<MarketDTO>> listAll(
            @RequestParam(required = false) Long dataSourceId,
            @RequestParam(required = false) String marketType,
            @RequestParam(required = false) Boolean enabled) {
        
        List<MarketDTO> result = marketService.listAll(dataSourceId, marketType, enabled);
        return ApiResponse.success(result);
    }

    /**
     * 获取市场详情
     * 
     * GET /api/v1/markets/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<MarketDTO> getById(@PathVariable Long id) {
        MarketDTO market = marketService.getById(id);
        return ApiResponse.success(market);
    }

    /**
     * 启用/禁用市场
     * 
     * PATCH /api/v1/markets/{id}/status
     * 
     * 禁用市场时会级联禁用该市场下所有交易对的同步
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<MarketDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request) {
        
        MarketDTO market = marketService.updateStatus(id, request.getEnabled());
        String message = request.getEnabled() ? "市场已启用" : "市场已禁用";
        return ApiResponse.success(message, market);
    }

    /**
     * 同步交易对列表
     * 
     * POST /api/v1/markets/{id}/sync-symbols
     * 
     * 从币安 exchangeInfo API 同步该市场下的所有交易对。
     * 新同步的交易对默认关闭实时同步和历史同步。
     * 
     * @param id 市场ID
     * @return 同步结果
     */
    @PostMapping("/{id}/sync-symbols")
    public ApiResponse<SymbolSyncResult> syncSymbols(@PathVariable Long id) {
        log.info("Syncing symbols for market: {}", id);
        SymbolSyncResult result = symbolService.syncSymbolsFromBinance(id);
        return ApiResponse.success(result.getMessage(), result);
    }
}

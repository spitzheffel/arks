package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chanlun.dto.*;
import com.chanlun.exception.BusinessException;
import com.chanlun.service.DataGapService;
import com.chanlun.service.GapFillService;
import com.chanlun.service.GapFillService.BatchGapFillResult;
import com.chanlun.service.GapFillService.GapFillResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据缺口管理控制器
 * 
 * 提供数据缺口检测、查询和回补的 REST API
 * 
 * API 路径: /api/v1/gaps
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/gaps")
@RequiredArgsConstructor
public class GapController {

    private final DataGapService dataGapService;
    private final GapFillService gapFillService;

    /**
     * 默认每页数量
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 最大每页数量
     */
    private static final int MAX_PAGE_SIZE = 100;

    // ==================== 缺口查询 API ====================

    /**
     * 获取缺口列表
     * 
     * GET /api/v1/gaps
     * 
     * @param symbolId 交易对ID（可选）
     * @param interval 时间周期（可选）
     * @param status 状态（可选）
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 缺口分页列表
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> getGapList(
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 规范化分页参数
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        
        IPage<DataGapDTO> gapPage = dataGapService.list(page, size, symbolId, interval, status);
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", gapPage.getRecords());
        result.put("total", gapPage.getTotal());
        result.put("page", gapPage.getCurrent());
        result.put("size", gapPage.getSize());
        result.put("pages", gapPage.getPages());
        
        return ApiResponse.success(result);
    }

    /**
     * 获取缺口详情
     * 
     * GET /api/v1/gaps/{id}
     * 
     * @param id 缺口ID
     * @return 缺口详情
     */
    @GetMapping("/{id}")
    public ApiResponse<DataGapDTO> getGap(@PathVariable Long id) {
        DataGapDTO gap = dataGapService.getById(id);
        return ApiResponse.success(gap);
    }

    // ==================== 缺口检测 API ====================

    /**
     * 检测数据缺口
     * 
     * POST /api/v1/gaps/detect
     * 
     * 支持两种模式：
     * 1. 单交易对检测：指定 symbolId 和 interval
     * 2. 批量检测：设置 detectAll = true
     * 
     * @param request 缺口检测请求
     * @return 检测结果
     */
    @PostMapping("/detect")
    public ApiResponse<GapDetectResult> detectGaps(@Valid @RequestBody GapDetectRequest request) {
        log.info("Gap detection request: {}", request);
        
        // 批量检测模式
        if (Boolean.TRUE.equals(request.getDetectAll())) {
            log.info("Starting batch gap detection for all eligible symbols");
            GapDetectResult result = dataGapService.detectAllGaps();
            return ApiResponse.success(result);
        }
        
        // 单交易对检测模式
        if (request.getSymbolId() == null) {
            throw new BusinessException("交易对ID不能为空");
        }
        if (!StringUtils.hasText(request.getInterval())) {
            throw new BusinessException("时间周期不能为空");
        }
        
        log.info("Starting gap detection for symbol {} interval {}", 
                request.getSymbolId(), request.getInterval());
        
        GapDetectResult result = dataGapService.detectGaps(
                request.getSymbolId(), 
                request.getInterval());
        
        return ApiResponse.success(result);
    }

    // ==================== 缺口回补 API ====================

    /**
     * 回补单个缺口
     * 
     * POST /api/v1/gaps/{id}/fill
     * 
     * @param id 缺口ID
     * @return 回补结果
     */
    @PostMapping("/{id}/fill")
    public ApiResponse<GapFillResult> fillGap(@PathVariable Long id) {
        log.info("Gap fill request: gapId={}", id);
        
        GapFillResult result = gapFillService.fillGap(id);
        
        if (result.isSuccess()) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error(500, result.getMessage(), result);
        }
    }

    /**
     * 批量回补缺口
     * 
     * POST /api/v1/gaps/batch-fill
     * 
     * @param request 批量回补请求（包含缺口ID列表）
     * @return 批量回补结果
     */
    @PostMapping("/batch-fill")
    public ApiResponse<BatchGapFillResult> batchFillGaps(@RequestBody Map<String, List<Long>> request) {
        List<Long> gapIds = request.get("gapIds");
        
        if (gapIds == null || gapIds.isEmpty()) {
            throw new BusinessException("缺口ID列表不能为空");
        }
        
        log.info("Batch gap fill request: gapIds={}", gapIds);
        
        BatchGapFillResult result = gapFillService.batchFillGaps(gapIds);
        return ApiResponse.success(result);
    }

    /**
     * 重置失败的缺口状态
     * 
     * POST /api/v1/gaps/{id}/reset
     * 
     * @param id 缺口ID
     * @return 重置后的缺口
     */
    @PostMapping("/{id}/reset")
    public ApiResponse<DataGapDTO> resetFailedGap(@PathVariable Long id) {
        log.info("Reset failed gap request: gapId={}", id);
        
        DataGapDTO gap = gapFillService.resetFailedGap(id);
        return ApiResponse.success(gap);
    }
}

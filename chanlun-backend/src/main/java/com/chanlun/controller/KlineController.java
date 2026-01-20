package com.chanlun.controller;

import com.chanlun.dto.ApiResponse;
import com.chanlun.dto.KlineDTO;
import com.chanlun.dto.KlineDeleteRequest;
import com.chanlun.dto.KlineDeleteResult;
import com.chanlun.entity.Kline;
import com.chanlun.service.KlineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * K线数据控制器
 * 
 * 提供 K 线数据查询 REST API
 * 
 * API 路径: /api/v1/klines
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/klines")
@RequiredArgsConstructor
public class KlineController {

    private final KlineService klineService;

    /**
     * 默认返回数量
     */
    private static final int DEFAULT_LIMIT = 500;

    /**
     * 最大返回数量
     */
    private static final int MAX_LIMIT = 1000;

    /**
     * 获取K线数据
     * 
     * GET /api/v1/klines
     * 
     * @param symbolId 交易对ID (必填)
     * @param interval 时间周期 (必填)
     * @param startTime 开始时间 (可选, ISO 8601 格式)
     * @param endTime 结束时间 (可选, ISO 8601 格式)
     * @param limit 返回数量 (默认500，最大1000)
     * @return K线数据列表
     */
    @GetMapping
    public ApiResponse<List<KlineDTO>> getKlines(
            @RequestParam Long symbolId,
            @RequestParam String interval,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(required = false) Integer limit) {
        
        // 处理 limit 参数
        int effectiveLimit = normalizeLimit(limit);
        
        List<Kline> klines;
        
        if (startTime != null && endTime != null) {
            // 有时间范围，使用带时间范围和限制的查询
            klines = klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    symbolId, interval, startTime, endTime, effectiveLimit);
        } else if (startTime != null) {
            // 只有开始时间，查询从开始时间到现在
            klines = klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                    symbolId, interval, startTime, Instant.now(), effectiveLimit);
        } else if (endTime != null) {
            // 只有结束时间，查询从最早到结束时间
            Instant earliest = klineService.getMinOpenTime(symbolId, interval);
            if (earliest != null) {
                klines = klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                        symbolId, interval, earliest, endTime, effectiveLimit);
            } else {
                klines = List.of();
            }
        } else {
            // 无时间范围，查询最新的 limit 条数据
            klines = getLatestKlines(symbolId, interval, effectiveLimit);
        }
        
        // 转换为 DTO
        List<KlineDTO> result = klines.stream()
                .map(KlineDTO::fromEntity)
                .collect(Collectors.toList());
        
        return ApiResponse.success(result);
    }

    /**
     * 获取最新的 K 线数据
     * 
     * 当没有指定时间范围时，返回最新的 limit 条数据
     */
    private List<Kline> getLatestKlines(Long symbolId, String interval, int limit) {
        // 获取最大开盘时间
        Instant maxOpenTime = klineService.getMaxOpenTime(symbolId, interval);
        if (maxOpenTime == null) {
            return List.of();
        }
        
        // 获取最小开盘时间
        Instant minOpenTime = klineService.getMinOpenTime(symbolId, interval);
        if (minOpenTime == null) {
            return List.of();
        }
        
        // 查询所有数据并限制数量（按时间升序）
        return klineService.getBySymbolIdAndIntervalAndTimeRangeWithLimit(
                symbolId, interval, minOpenTime, maxOpenTime, limit);
    }

    /**
     * 规范化 limit 参数
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 删除指定交易对的历史K线数据（物理删除）
     * 
     * DELETE /api/v1/klines/symbols/{symbolId}
     * 
     * 注意：此操作为物理删除，不可恢复，需前端二次确认
     * 
     * @param symbolId 交易对ID (必填)
     * @param request 删除请求参数
     * @return 删除结果
     */
    @DeleteMapping("/symbols/{symbolId}")
    public ApiResponse<KlineDeleteResult> deleteKlines(
            @PathVariable Long symbolId,
            @Valid @RequestBody KlineDeleteRequest request) {
        
        log.info("Deleting klines: symbolId={}, interval={}, timeRange=[{}, {}]",
                symbolId, request.getInterval(), request.getStartTime(), request.getEndTime());
        
        int deletedCount = klineService.deleteByTimeRange(
                symbolId, 
                request.getInterval(), 
                request.getStartTime(), 
                request.getEndTime());
        
        KlineDeleteResult result = KlineDeleteResult.builder()
                .symbolId(symbolId)
                .interval(request.getInterval())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .deletedCount(deletedCount)
                .build();
        
        return ApiResponse.success(result);
    }
}

package com.chanlun.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.chanlun.dto.*;
import com.chanlun.entity.SyncStatus;
import com.chanlun.entity.SyncTask;
import com.chanlun.exception.BusinessException;
import com.chanlun.service.HistorySyncService;
import com.chanlun.service.SyncService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 同步管理控制器
 * 
 * 提供同步任务和同步状态管理的 REST API
 * 
 * API 路径: /api/v1/sync
 * 
 * @author Chanlun Team
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final HistorySyncService historySyncService;

    /**
     * 默认每页数量
     */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 最大每页数量
     */
    private static final int MAX_PAGE_SIZE = 100;

    // ==================== 同步任务 API ====================

    /**
     * 获取同步任务列表
     * 
     * GET /api/v1/sync/tasks
     * 
     * @param symbolId 交易对ID（可选）
     * @param taskType 任务类型（可选）
     * @param status 状态（可选）
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 同步任务分页列表
     */
    @GetMapping("/tasks")
    public ApiResponse<Map<String, Object>> getTaskList(
            @RequestParam(required = false) Long symbolId,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 规范化分页参数
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        
        IPage<SyncTask> taskPage = syncService.getTaskList(symbolId, taskType, status, page, size);
        
        List<SyncTaskDTO> tasks = taskPage.getRecords().stream()
                .map(SyncTaskDTO::fromEntity)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", tasks);
        result.put("total", taskPage.getTotal());
        result.put("page", taskPage.getCurrent());
        result.put("size", taskPage.getSize());
        result.put("pages", taskPage.getPages());
        
        return ApiResponse.success(result);
    }

    /**
     * 获取同步任务详情
     * 
     * GET /api/v1/sync/tasks/{id}
     * 
     * @param id 任务ID
     * @return 同步任务详情
     */
    @GetMapping("/tasks/{id}")
    public ApiResponse<SyncTaskDTO> getTask(@PathVariable Long id) {
        SyncTask task = syncService.getTaskById(id);
        if (task == null) {
            return ApiResponse.notFound("同步任务不存在");
        }
        return ApiResponse.success(SyncTaskDTO.fromEntity(task));
    }

    /**
     * 触发历史同步
     * 
     * POST /api/v1/sync/symbols/{symbolId}/history
     * 
     * 手动触发指定交易对的历史数据同步，必须指定时间范围。
     * 同步过程会分段处理，单次跨度不超过 30 天。
     * 
     * @param symbolId 交易对ID
     * @param request 历史同步请求（包含 interval、startTime、endTime）
     * @return 同步结果
     */
    @PostMapping("/symbols/{symbolId}/history")
    public ApiResponse<HistorySyncResult> triggerHistorySync(
            @PathVariable Long symbolId,
            @Valid @RequestBody HistorySyncRequest request) {
        
        log.info("Triggering history sync: symbolId={}, interval={}, timeRange=[{}, {}]",
                symbolId, request.getInterval(), request.getStartTime(), request.getEndTime());
        
        // 校验时间范围
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException("开始时间不能晚于结束时间");
        }
        
        // 校验结束时间不能晚于当前时间
        if (request.getEndTime().isAfter(Instant.now())) {
            throw new BusinessException("结束时间不能晚于当前时间");
        }
        
        long startMs = System.currentTimeMillis();
        
        try {
            // 执行历史同步
            int syncedCount = historySyncService.syncHistory(
                    symbolId,
                    request.getInterval(),
                    request.getStartTime(),
                    request.getEndTime());
            
            long durationMs = System.currentTimeMillis() - startMs;
            
            // 获取最新创建的任务 ID
            IPage<SyncTask> tasks = syncService.getTaskList(symbolId, SyncTask.TaskType.HISTORY, null, 1, 1);
            Long taskId = tasks.getRecords().isEmpty() ? null : tasks.getRecords().get(0).getId();
            
            HistorySyncResult result = HistorySyncResult.success(
                    taskId,
                    symbolId,
                    request.getInterval(),
                    request.getStartTime(),
                    request.getEndTime(),
                    syncedCount,
                    durationMs);
            
            return ApiResponse.success(result);
            
        } catch (BusinessException e) {
            log.error("History sync failed: {}", e.getMessage());
            HistorySyncResult result = HistorySyncResult.failure(
                    symbolId,
                    request.getInterval(),
                    request.getStartTime(),
                    request.getEndTime(),
                    e.getMessage());
            return ApiResponse.error(400, e.getMessage(), result);
        }
    }

    // ==================== 同步状态 API ====================

    /**
     * 获取同步状态列表
     * 
     * GET /api/v1/sync/status
     * 
     * @param symbolId 交易对ID（可选）
     * @param page 页码（从1开始）
     * @param size 每页数量
     * @return 同步状态分页列表
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> getSyncStatusList(
            @RequestParam(required = false) Long symbolId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        // 规范化分页参数
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        
        IPage<SyncStatus> statusPage = syncService.getSyncStatusList(symbolId, page, size);
        
        List<SyncStatusDTO> statuses = statusPage.getRecords().stream()
                .map(SyncStatusDTO::fromEntity)
                .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", statuses);
        result.put("total", statusPage.getTotal());
        result.put("page", statusPage.getCurrent());
        result.put("size", statusPage.getSize());
        result.put("pages", statusPage.getPages());
        
        return ApiResponse.success(result);
    }

    /**
     * 获取同步状态详情
     * 
     * GET /api/v1/sync/status/{id}
     * 
     * @param id 同步状态ID
     * @return 同步状态详情
     */
    @GetMapping("/status/{id}")
    public ApiResponse<SyncStatusDTO> getSyncStatus(@PathVariable Long id) {
        SyncStatus status = syncService.getSyncStatusById(id);
        if (status == null) {
            return ApiResponse.notFound("同步状态不存在");
        }
        return ApiResponse.success(SyncStatusDTO.fromEntity(status));
    }

    /**
     * 获取交易对的所有同步状态
     * 
     * GET /api/v1/sync/symbols/{symbolId}/status
     * 
     * @param symbolId 交易对ID
     * @return 同步状态列表
     */
    @GetMapping("/symbols/{symbolId}/status")
    public ApiResponse<List<SyncStatusDTO>> getSyncStatusBySymbol(@PathVariable Long symbolId) {
        List<SyncStatus> statuses = syncService.getSyncStatusBySymbolId(symbolId);
        List<SyncStatusDTO> result = statuses.stream()
                .map(SyncStatusDTO::fromEntity)
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * 更新周期级别自动回补开关
     * 
     * PATCH /api/v1/sync/status/{id}/auto-gap-fill
     * 
     * @param id 同步状态ID
     * @param request 自动回补开关请求
     * @return 更新后的同步状态
     */
    @PatchMapping("/status/{id}/auto-gap-fill")
    public ApiResponse<SyncStatusDTO> updateAutoGapFill(
            @PathVariable Long id,
            @Valid @RequestBody AutoGapFillRequest request) {
        
        log.info("Updating auto_gap_fill_enabled: id={}, enabled={}", id, request.getEnabled());
        
        syncService.updateAutoGapFillEnabledById(id, request.getEnabled());
        
        SyncStatus status = syncService.getSyncStatusById(id);
        return ApiResponse.success(SyncStatusDTO.fromEntity(status));
    }
}

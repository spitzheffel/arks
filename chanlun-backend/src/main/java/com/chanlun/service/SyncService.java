package com.chanlun.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chanlun.entity.SyncStatus;
import com.chanlun.entity.SyncTask;
import com.chanlun.exception.BusinessException;
import com.chanlun.exception.ResourceNotFoundException;
import com.chanlun.mapper.SyncStatusMapper;
import com.chanlun.mapper.SyncTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 同步服务
 * 
 * 提供同步任务和同步状态的管理功能：
 * - 创建同步任务
 * - 更新任务状态
 * - 查询同步状态
 * - 更新同步状态
 * 
 * @author Chanlun Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final SyncTaskMapper syncTaskMapper;
    private final SyncStatusMapper syncStatusMapper;

    /**
     * 支持的 K 线周期列表（不支持 1s）
     */
    private static final Set<String> VALID_INTERVALS = Set.of(
            "1m", "3m", "5m", "15m", "30m",
            "1h", "2h", "4h", "6h", "8h", "12h",
            "1d", "3d", "1w", "1M"
    );

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    // ==================== 同步任务管理 ====================

    /**
     * 创建同步任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param taskType 任务类型 (REALTIME/HISTORY/GAP_FILL)
     * @param startTime 同步起始时间
     * @param endTime 同步结束时间
     * @return 创建的同步任务
     */
    @Transactional
    public SyncTask createTask(Long symbolId, String interval, String taskType, 
                               Instant startTime, Instant endTime) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        validateTaskType(taskType);

        SyncTask task = SyncTask.builder()
                .symbolId(symbolId)
                .interval(interval)
                .taskType(taskType)
                .status(SyncTask.Status.PENDING)
                .startTime(startTime)
                .endTime(endTime)
                .syncedCount(0)
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .build();

        syncTaskMapper.insert(task);
        log.info("Created sync task: id={}, symbolId={}, interval={}, taskType={}", 
                task.getId(), symbolId, interval, taskType);
        return task;
    }

    /**
     * 创建历史同步任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 同步起始时间
     * @param endTime 同步结束时间
     * @return 创建的同步任务
     */
    @Transactional
    public SyncTask createHistoryTask(Long symbolId, String interval, Instant startTime, Instant endTime) {
        validateTimeRange(startTime, endTime);
        return createTask(symbolId, interval, SyncTask.TaskType.HISTORY, startTime, endTime);
    }

    /**
     * 创建缺口回补任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param startTime 缺口起始时间
     * @param endTime 缺口结束时间
     * @return 创建的同步任务
     */
    @Transactional
    public SyncTask createGapFillTask(Long symbolId, String interval, Instant startTime, Instant endTime) {
        validateTimeRange(startTime, endTime);
        return createTask(symbolId, interval, SyncTask.TaskType.GAP_FILL, startTime, endTime);
    }

    /**
     * 创建实时同步任务
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 创建的同步任务
     */
    @Transactional
    public SyncTask createRealtimeTask(Long symbolId, String interval) {
        return createTask(symbolId, interval, SyncTask.TaskType.REALTIME, null, null);
    }

    /**
     * 更新任务状态为运行中
     * 
     * @param taskId 任务ID
     * @return 是否更新成功
     */
    @Transactional
    public boolean startTask(Long taskId) {
        int updated = syncTaskMapper.updateStatus(taskId, SyncTask.Status.RUNNING);
        if (updated > 0) {
            log.debug("Task started: id={}", taskId);
        }
        return updated > 0;
    }

    /**
     * 更新任务状态为成功
     * 
     * @param taskId 任务ID
     * @param syncedCount 已同步数量
     * @return 是否更新成功
     */
    @Transactional
    public boolean completeTask(Long taskId, int syncedCount) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        
        task.setStatus(SyncTask.Status.SUCCESS);
        task.setSyncedCount(syncedCount);
        task.setUpdatedAt(Instant.now());
        
        int updated = syncTaskMapper.updateById(task);
        if (updated > 0) {
            log.info("Task completed: id={}, syncedCount={}", taskId, syncedCount);
        }
        return updated > 0;
    }

    /**
     * 更新任务状态为失败
     * 
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     * @return 是否更新成功
     */
    @Transactional
    public boolean failTask(Long taskId, String errorMessage) {
        int updated = syncTaskMapper.updateStatusAndError(taskId, SyncTask.Status.FAILED, errorMessage);
        if (updated > 0) {
            log.warn("Task failed: id={}, error={}", taskId, errorMessage);
        }
        return updated > 0;
    }

    /**
     * 重试任务
     * 
     * @param taskId 任务ID
     * @return 是否可以重试
     */
    @Transactional
    public boolean retryTask(Long taskId) {
        SyncTask task = syncTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        
        if (task.getRetryCount() >= task.getMaxRetries()) {
            log.warn("Task reached max retries: id={}, retryCount={}, maxRetries={}", 
                    taskId, task.getRetryCount(), task.getMaxRetries());
            return false;
        }
        
        syncTaskMapper.incrementRetryCount(taskId);
        syncTaskMapper.updateStatus(taskId, SyncTask.Status.PENDING);
        log.info("Task queued for retry: id={}, retryCount={}", taskId, task.getRetryCount() + 1);
        return true;
    }

    /**
     * 更新已同步数量
     * 
     * @param taskId 任务ID
     * @param syncedCount 已同步数量
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateSyncedCount(Long taskId, int syncedCount) {
        return syncTaskMapper.updateSyncedCount(taskId, syncedCount) > 0;
    }

    /**
     * 根据ID获取同步任务
     * 
     * @param taskId 任务ID
     * @return 同步任务
     */
    public SyncTask getTaskById(Long taskId) {
        return syncTaskMapper.selectById(taskId);
    }

    /**
     * 获取同步任务列表（分页）
     * 
     * @param symbolId 交易对ID（可选）
     * @param taskType 任务类型（可选）
     * @param status 状态（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    public IPage<SyncTask> getTaskList(Long symbolId, String taskType, String status, int page, int size) {
        LambdaQueryWrapper<SyncTask> wrapper = new LambdaQueryWrapper<>();
        
        if (symbolId != null) {
            wrapper.eq(SyncTask::getSymbolId, symbolId);
        }
        if (taskType != null && !taskType.isEmpty()) {
            wrapper.eq(SyncTask::getTaskType, taskType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SyncTask::getStatus, status);
        }
        
        wrapper.orderByDesc(SyncTask::getCreatedAt);
        
        return syncTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 获取待处理的任务
     * 
     * @param limit 限制数量
     * @return 待处理任务列表
     */
    public List<SyncTask> getPendingTasks(int limit) {
        return syncTaskMapper.selectPendingTasks(limit);
    }

    /**
     * 获取运行中的任务
     * 
     * @return 运行中任务列表
     */
    public List<SyncTask> getRunningTasks() {
        return syncTaskMapper.selectRunningTasks();
    }

    /**
     * 获取可重试的失败任务
     * 
     * @param limit 限制数量
     * @return 可重试任务列表
     */
    public List<SyncTask> getRetryableTasks(int limit) {
        return syncTaskMapper.selectRetryableTasks(limit);
    }

    // ==================== 同步状态管理 ====================

    /**
     * 获取或创建同步状态
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 同步状态
     */
    @Transactional
    public SyncStatus getOrCreateSyncStatus(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        SyncStatus status = syncStatusMapper.selectBySymbolIdAndInterval(symbolId, interval);
        if (status == null) {
            status = SyncStatus.builder()
                    .symbolId(symbolId)
                    .interval(interval)
                    .totalKlines(0L)
                    .autoGapFillEnabled(true)
                    .build();
            syncStatusMapper.insert(status);
            log.debug("Created sync status: symbolId={}, interval={}", symbolId, interval);
        }
        return status;
    }

    /**
     * 根据ID获取同步状态
     * 
     * @param id 同步状态ID
     * @return 同步状态
     */
    public SyncStatus getSyncStatusById(Long id) {
        return syncStatusMapper.selectById(id);
    }

    /**
     * 根据交易对ID和周期获取同步状态
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @return 同步状态
     */
    public SyncStatus getSyncStatus(Long symbolId, String interval) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        return syncStatusMapper.selectBySymbolIdAndInterval(symbolId, interval);
    }

    /**
     * 获取交易对的所有同步状态
     * 
     * @param symbolId 交易对ID
     * @return 同步状态列表
     */
    public List<SyncStatus> getSyncStatusBySymbolId(Long symbolId) {
        validateSymbolId(symbolId);
        return syncStatusMapper.selectBySymbolId(symbolId);
    }

    /**
     * 获取同步状态列表（分页）
     * 
     * @param symbolId 交易对ID（可选）
     * @param page 页码
     * @param size 每页数量
     * @return 分页结果
     */
    public IPage<SyncStatus> getSyncStatusList(Long symbolId, int page, int size) {
        LambdaQueryWrapper<SyncStatus> wrapper = new LambdaQueryWrapper<>();
        
        if (symbolId != null) {
            wrapper.eq(SyncStatus::getSymbolId, symbolId);
        }
        
        wrapper.orderByAsc(SyncStatus::getSymbolId)
               .orderByAsc(SyncStatus::getInterval);
        
        return syncStatusMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 更新同步状态（同步成功后调用）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param lastKlineTime 最后K线时间
     * @param syncedCount 本次同步数量
     */
    @Transactional
    public void updateSyncStatus(Long symbolId, String interval, Instant lastKlineTime, long syncedCount) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        SyncStatus status = getOrCreateSyncStatus(symbolId, interval);
        
        // 更新 last_sync_time
        status.setLastSyncTime(Instant.now());
        
        // 更新 last_kline_time（取 max）
        if (lastKlineTime != null) {
            if (status.getLastKlineTime() == null || lastKlineTime.isAfter(status.getLastKlineTime())) {
                status.setLastKlineTime(lastKlineTime);
            }
        }
        
        // 增量更新 total_klines
        status.setTotalKlines(status.getTotalKlines() + syncedCount);
        
        syncStatusMapper.updateById(status);
        log.debug("Updated sync status: symbolId={}, interval={}, lastKlineTime={}, totalKlines={}", 
                symbolId, interval, status.getLastKlineTime(), status.getTotalKlines());
    }

    /**
     * 更新自动缺口回补开关
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param enabled 是否启用
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateAutoGapFillEnabled(Long symbolId, String interval, boolean enabled) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        // 确保同步状态存在
        getOrCreateSyncStatus(symbolId, interval);
        
        int updated = syncStatusMapper.updateAutoGapFillEnabled(symbolId, interval, enabled);
        if (updated > 0) {
            log.info("Updated auto_gap_fill_enabled: symbolId={}, interval={}, enabled={}", 
                    symbolId, interval, enabled);
        }
        return updated > 0;
    }

    /**
     * 根据同步状态ID更新自动缺口回补开关
     * 
     * @param id 同步状态ID
     * @param enabled 是否启用
     * @return 是否更新成功
     */
    @Transactional
    public boolean updateAutoGapFillEnabledById(Long id, boolean enabled) {
        SyncStatus status = syncStatusMapper.selectById(id);
        if (status == null) {
            throw new ResourceNotFoundException("同步状态不存在: id=" + id);
        }
        
        status.setAutoGapFillEnabled(enabled);
        int updated = syncStatusMapper.updateById(status);
        if (updated > 0) {
            log.info("Updated auto_gap_fill_enabled: id={}, symbolId={}, interval={}, enabled={}", 
                    id, status.getSymbolId(), status.getInterval(), enabled);
        }
        return updated > 0;
    }

    /**
     * 重置同步状态（删除数据后调用）
     * 
     * @param symbolId 交易对ID
     * @param interval 时间周期
     * @param lastKlineTime 新的最后K线时间（可为 null）
     * @param totalKlines 新的总K线数量
     */
    @Transactional
    public void resetSyncStatus(Long symbolId, String interval, Instant lastKlineTime, long totalKlines) {
        validateSymbolId(symbolId);
        validateInterval(interval);
        
        syncStatusMapper.updateLastKlineTime(symbolId, interval, lastKlineTime);
        syncStatusMapper.updateTotalKlines(symbolId, interval, totalKlines);
        syncStatusMapper.updateAutoGapFillEnabled(symbolId, interval, false);
        
        log.info("Reset sync status: symbolId={}, interval={}, lastKlineTime={}, totalKlines={}, autoGapFillEnabled=false", 
                symbolId, interval, lastKlineTime, totalKlines);
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
        if (interval == null || interval.isEmpty()) {
            throw new BusinessException("时间周期不能为空");
        }
        if (!VALID_INTERVALS.contains(interval)) {
            throw new BusinessException("不支持的时间周期: " + interval + 
                    "。支持的周期: " + String.join(", ", VALID_INTERVALS));
        }
    }

    /**
     * 校验任务类型
     */
    private void validateTaskType(String taskType) {
        if (taskType == null || taskType.isEmpty()) {
            throw new BusinessException("任务类型不能为空");
        }
        if (!SyncTask.TaskType.REALTIME.equals(taskType) && 
            !SyncTask.TaskType.HISTORY.equals(taskType) && 
            !SyncTask.TaskType.GAP_FILL.equals(taskType)) {
            throw new BusinessException("不支持的任务类型: " + taskType);
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
}
